package org.example.ai.business.index;

import lombok.extern.slf4j.Slf4j;
import org.example.ai.business.remote.PublicBusinessClient;
import org.example.ai.business.remote.RemoteBusinessProduct;
import org.example.ai.business.remote.RemoteBusinessProductPage;
import org.example.ai.business.remote.RemoteCompanyPage;
import org.example.ai.business.remote.RemotePublicCompany;
import org.example.ai.provider.EmbeddingProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class BusinessIndexer {

    private final PublicBusinessClient client;
    private final EmbeddingProvider embeddings;
    private final BusinessTextBuilder textBuilder;
    private final CompanyEmbeddingRepository repository;
    private final JdbcTemplate jdbc;
    private final ThreadPoolTaskScheduler scheduler;
    private final boolean enabled;
    private final int pageSize;
    private final int maxPages;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public BusinessIndexer(
            PublicBusinessClient client,
            EmbeddingProvider embeddings,
            BusinessTextBuilder textBuilder,
            CompanyEmbeddingRepository repository,
            JdbcTemplate jdbc,
            @Qualifier("aiIndexerScheduler") ThreadPoolTaskScheduler scheduler,
            @Value("${ai.business-indexer.enabled:true}") boolean enabled,
            @Value("${ai.business-indexer.page-size:100}") int pageSize,
            @Value("${ai.business-indexer.max-pages:1000}") int maxPages,
            @Value("${ai.embedding.batch-size:32}") int batchSize) {
        this.client = client;
        this.embeddings = embeddings;
        this.textBuilder = textBuilder;
        this.repository = repository;
        this.jdbc = jdbc;
        this.scheduler = scheduler;
        this.enabled = enabled;
        this.pageSize = Math.max(1, Math.min(pageSize, 100));
        this.maxPages = Math.max(1, maxPages);
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(
            initialDelayString = "${ai.business-indexer.initial-delay-ms:120000}",
            fixedDelayString = "${ai.business-indexer.interval-ms:1800000}")
    public void scheduledReindex() {
        if (enabled) reindex("scheduled");
    }

    public boolean triggerAsyncReindex() {
        if (!running.compareAndSet(false, true)) return false;
        try {
            scheduler.execute(() -> runReserved("admin"));
            return true;
        } catch (RuntimeException rejected) {
            running.set(false);
            log.warn("Business reindex could not be scheduled: {}", rejected.getMessage());
            return false;
        }
    }

    public Outcome reindex(String trigger) {
        if (!running.compareAndSet(false, true)) return Outcome.SKIPPED;
        return runReserved(trigger);
    }

    public boolean isRunning() {
        return running.get();
    }

    public BusinessIndexStatus status() {
        List<BusinessIndexStatus> rows = jdbc.query(
                "SELECT last_run_at, last_status, notes FROM business_index_state ORDER BY last_run_at DESC, id DESC LIMIT 1",
                (rs, rowNum) -> new BusinessIndexStatus(running.get(), repository.count(),
                        rs.getTimestamp("last_run_at").toInstant(), rs.getString("last_status"), rs.getString("notes")));
        return rows.isEmpty()
                ? new BusinessIndexStatus(running.get(), repository.count(), null, "NEVER_RUN", null)
                : rows.get(0);
    }

    private Outcome runReserved(String trigger) {
        try {
            return doReindex(trigger);
        } finally {
            running.set(false);
        }
    }

    private Outcome doReindex(String trigger) {
        Instant started = Instant.now();
        try {
            Crawl<RemotePublicCompany> companyCrawl = crawlCompanies();
            Crawl<RemoteBusinessProduct> productCrawl = crawlProducts();

            // Every company capability row is an aggregate over the complete public product
            // catalog. A truncated product crawl cannot prove product count, categories, regions,
            // or price range for any company, so updating rows would overwrite known-complete
            // projections with partial data. Preserve the current index and retry next run.
            if (!productCrawl.complete()) {
                String notes = String.format(
                        "trigger=%s companiesSeen=%d productsSeen=%d complete=false "
                                + "updates=skipped_incomplete_product_crawl durationMs=%d",
                        trigger, companyCrawl.items().size(), productCrawl.items().size(),
                        Duration.between(started, Instant.now()).toMillis());
                record(Outcome.PARTIAL, (int) repository.count(), notes);
                return Outcome.PARTIAL;
            }

            Map<Long, RemotePublicCompany> companies = new HashMap<>();
            for (RemotePublicCompany company : companyCrawl.items()) {
                if (company.indexable()) companies.put(company.id(), company);
            }
            Map<Long, List<RemoteBusinessProduct>> productsByCompany = new HashMap<>();
            for (RemoteBusinessProduct product : productCrawl.items()) {
                if (product.publiclyVisible() && companies.containsKey(product.companyId())) {
                    productsByCompany.computeIfAbsent(product.companyId(), ignored -> new ArrayList<>()).add(product);
                }
            }

            Map<Long, String> existingHashes = repository.findAllContentHashes();
            Set<Long> seen = new HashSet<>();
            List<Pending> pending = new ArrayList<>();
            List<RemotePublicCompany> orderedCompanies = companies.values().stream()
                    .sorted(Comparator.comparing(RemotePublicCompany::id))
                    .toList();
            for (RemotePublicCompany company : orderedCompanies) {
                CompanyProjection projection = project(company,
                        productsByCompany.getOrDefault(company.id(), List.of()));
                seen.add(company.id());
                if (!projection.hash().equals(existingHashes.get(company.id()))) pending.add(new Pending(projection));
            }

            int embedded = 0;
            int failed = 0;
            for (int offset = 0; offset < pending.size(); offset += batchSize) {
                List<Pending> chunk = pending.subList(offset, Math.min(offset + batchSize, pending.size()));
                try {
                    List<float[]> vectors = embeddings.embedDocuments(chunk.stream().map(p -> p.projection().text()).toList());
                    if (vectors == null || vectors.size() != chunk.size()
                            || vectors.stream().anyMatch(vector -> vector == null)) {
                        throw new IllegalStateException("Embedding batch cardinality mismatch");
                    }
                    for (int i = 0; i < chunk.size(); i++) repository.upsert(toRow(chunk.get(i).projection(), vectors.get(i)));
                    embedded += chunk.size();
                } catch (RuntimeException e) {
                    failed += chunk.size();
                    log.warn("Business embedding batch failed ({} companies): {}", chunk.size(), e.getMessage());
                }
            }

            boolean complete = companyCrawl.complete() && productCrawl.complete();
            int removed = 0;
            if (complete) {
                Set<Long> stale = new HashSet<>(existingHashes.keySet());
                stale.removeAll(seen);
                removed = repository.deleteByCompanyIds(stale);
            }

            Outcome outcome = !complete || failed > 0 ? (embedded > 0 || failed == 0 ? Outcome.PARTIAL : Outcome.FAILURE) : Outcome.SUCCESS;
            String notes = String.format(
                    "trigger=%s companies=%d visibleProducts=%d changed=%d embedded=%d failed=%d removed=%d complete=%s durationMs=%d",
                    trigger, seen.size(), productCrawl.items().stream().filter(RemoteBusinessProduct::publiclyVisible).count(),
                    pending.size(), embedded, failed, removed, complete, Duration.between(started, Instant.now()).toMillis());
            record(outcome, (int) repository.count(), notes);
            return outcome;
        } catch (RuntimeException e) {
            String notes = "trigger=" + trigger + " error=" + e.getMessage();
            record(Outcome.FAILURE, (int) repository.count(), notes);
            log.warn("Business index refresh failed and was isolated: {}", e.getMessage());
            return Outcome.FAILURE;
        }
    }

    private Crawl<RemotePublicCompany> crawlCompanies() {
        List<RemotePublicCompany> all = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        Integer advertisedTotalPages = null;
        Long advertisedTotalElements = null;
        int rawItems = 0;
        boolean stable = true;
        boolean complete = false;
        for (int page = 1; page <= maxPages; page++) {
            RemoteCompanyPage response = client.fetchVerifiedCompanies(page, pageSize);
            if (response == null || response.content() == null) {
                throw new IllegalStateException("Company endpoint returned an incomplete page " + page);
            }
            List<RemotePublicCompany> items = response.content();
            if (response.number() != null && response.number() != page - 1) stable = false;
            if (response.totalPages() != null) {
                if (response.totalPages() < 0) throw new IllegalStateException("Company endpoint returned invalid totalPages");
                if (advertisedTotalPages != null && !advertisedTotalPages.equals(response.totalPages())) stable = false;
                advertisedTotalPages = response.totalPages();
            }
            if (response.totalElements() != null) {
                if (response.totalElements() < 0) throw new IllegalStateException("Company endpoint returned invalid totalElements");
                if (advertisedTotalElements != null && !advertisedTotalElements.equals(response.totalElements())) stable = false;
                advertisedTotalElements = response.totalElements();
            }
            if (items.isEmpty()) {
                if (page == 1 && isAdvertisedEmpty(advertisedTotalPages, advertisedTotalElements)) {
                    complete = stable;
                    break;
                }
                if (advertisedTotalPages == null && advertisedTotalElements == null) break;
                throw new IllegalStateException("Company endpoint returned an empty page before the advertised end");
            }
            if (Integer.valueOf(0).equals(advertisedTotalPages)) {
                throw new IllegalStateException("Company endpoint returned items while totalPages was zero");
            }
            all.addAll(items);
            rawItems += items.size();
            if (advertisedTotalElements != null && rawItems > advertisedTotalElements) stable = false;
            for (RemotePublicCompany item : items) {
                if (item == null || item.id() == null || !ids.add(item.id())) {
                    stable = false;
                }
            }
            if (reachedEnd(page, rawItems, advertisedTotalPages, advertisedTotalElements)) {
                complete = stable && totalsAgreeAtEnd(page, rawItems, advertisedTotalPages, advertisedTotalElements);
                break;
            }
            if (advertisedTotalPages == null && advertisedTotalElements == null && items.size() < pageSize) {
                complete = stable;
                break;
            }
        }
        return new Crawl<>(List.copyOf(all), complete);
    }

    private Crawl<RemoteBusinessProduct> crawlProducts() {
        List<RemoteBusinessProduct> all = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        Integer advertisedTotalPages = null;
        Long advertisedTotalElements = null;
        int rawItems = 0;
        boolean stable = true;
        boolean complete = false;
        for (int page = 1; page <= maxPages; page++) {
            RemoteBusinessProductPage response = client.fetchProducts(page, pageSize);
            if (response == null || response.items() == null) {
                throw new IllegalStateException("Product endpoint returned an incomplete page " + page);
            }
            List<RemoteBusinessProduct> items = response.items();
            if (response.page() != null && response.page() != page) stable = false;
            if (response.totalPages() != null) {
                if (response.totalPages() < 0) throw new IllegalStateException("Product endpoint returned invalid total_pages");
                if (advertisedTotalPages != null && !advertisedTotalPages.equals(response.totalPages())) stable = false;
                advertisedTotalPages = response.totalPages();
            }
            if (response.totalElements() != null) {
                if (response.totalElements() < 0) throw new IllegalStateException("Product endpoint returned invalid total_elements");
                if (advertisedTotalElements != null && !advertisedTotalElements.equals(response.totalElements())) stable = false;
                advertisedTotalElements = response.totalElements();
            }
            if (items.isEmpty()) {
                if (page == 1 && isAdvertisedEmpty(advertisedTotalPages, advertisedTotalElements)) {
                    complete = stable;
                    break;
                }
                if (advertisedTotalPages == null && advertisedTotalElements == null) break;
                throw new IllegalStateException("Product endpoint returned an empty page before the advertised end");
            }
            if (Integer.valueOf(0).equals(advertisedTotalPages)) {
                throw new IllegalStateException("Product endpoint returned items while total_pages was zero");
            }
            all.addAll(items);
            rawItems += items.size();
            if (advertisedTotalElements != null && rawItems > advertisedTotalElements) stable = false;
            for (RemoteBusinessProduct item : items) {
                if (item == null || item.id() == null || !ids.add(item.id())) {
                    stable = false;
                }
            }
            if (reachedEnd(page, rawItems, advertisedTotalPages, advertisedTotalElements)) {
                complete = stable && totalsAgreeAtEnd(page, rawItems, advertisedTotalPages, advertisedTotalElements);
                break;
            }
            if (advertisedTotalPages == null && advertisedTotalElements == null && items.size() < pageSize) {
                complete = stable;
                break;
            }
        }
        return new Crawl<>(List.copyOf(all), complete);
    }

    private boolean isAdvertisedEmpty(Integer totalPages, Long totalElements) {
        boolean hasMetadata = totalPages != null || totalElements != null;
        return hasMetadata && (totalPages == null || totalPages == 0)
                && (totalElements == null || totalElements == 0);
    }

    private boolean reachedEnd(int page, int rawItems, Integer totalPages, Long totalElements) {
        return totalPages != null && page >= totalPages
                || totalElements != null && rawItems >= totalElements;
    }

    private boolean totalsAgreeAtEnd(int page, int rawItems, Integer totalPages, Long totalElements) {
        boolean pagesReached = totalPages == null || page >= totalPages;
        boolean elementsReached = totalElements == null || rawItems >= totalElements;
        return pagesReached && elementsReached;
    }

    private CompanyProjection project(RemotePublicCompany company, List<RemoteBusinessProduct> products) {
        List<RemoteBusinessProduct> sorted = products.stream().sorted(Comparator.comparing(RemoteBusinessProduct::id)).toList();
        List<Long> categories = sorted.stream().map(RemoteBusinessProduct::categoryId).filter(id -> id != null).distinct().sorted().toList();
        List<Long> regions = sorted.stream().map(RemoteBusinessProduct::regionId).filter(id -> id != null).distinct().sorted().toList();
        Double min = sorted.stream().map(RemoteBusinessProduct::price).filter(price -> price != null).min(Double::compareTo).orElse(null);
        Double max = sorted.stream().map(RemoteBusinessProduct::price).filter(price -> price != null).max(Double::compareTo).orElse(null);
        String text = textBuilder.build(company, sorted);
        // logoUrl is a stored result projection. Include it in the hash input so an image-only
        // company update is picked up by the normal skip-unchanged index pass.
        String hashText = text + "\nlogoUrl=" + (company.logoUrl() == null ? "" : company.logoUrl());
        String hash = textBuilder.hash(hashText, company.verificationStatus(), categories, regions,
                sorted.size(), min, max);
        return new CompanyProjection(company, categories, regions, sorted.size(), min, max, text, hash);
    }

    private CompanyEmbeddingRow toRow(CompanyProjection p, float[] vector) {
        return new CompanyEmbeddingRow(p.company().id(), p.company().slug(), p.company().name(),
                p.company().logoUrl(), p.company().verificationStatus(), p.categories(), p.regions(), p.productCount(),
                p.minPrice(), p.maxPrice(), p.hash(), vector);
    }

    private void record(Outcome outcome, int count, String notes) {
        try {
            jdbc.update("INSERT INTO business_index_state(last_status, companies_indexed, notes) VALUES (?, ?, ?)",
                    outcome.name(), count, notes);
        } catch (RuntimeException e) {
            log.warn("Could not record business index status: {}", e.getMessage());
        }
    }

    public enum Outcome { SUCCESS, PARTIAL, FAILURE, SKIPPED }

    private record Crawl<T>(List<T> items, boolean complete) {}
    private record Pending(CompanyProjection projection) {}
    private record CompanyProjection(RemotePublicCompany company, List<Long> categories, List<Long> regions,
                                     int productCount, Double minPrice, Double maxPrice, String text, String hash) {}
}
