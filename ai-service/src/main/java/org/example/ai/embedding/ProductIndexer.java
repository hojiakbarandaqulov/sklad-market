package org.example.ai.embedding;

import lombok.extern.slf4j.Slf4j;
import org.example.ai.gateway.dto.RemoteCategoryDto;
import org.example.ai.gateway.dto.RemoteIndexProductDto;
import org.example.ai.gateway.dto.RemoteProductListResponse;
import org.example.ai.gateway.dto.RemoteSpringPage;
import org.example.ai.provider.EmbeddingProvider;
import org.example.entity.IndexRun;
import org.example.repository.IndexRunRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Content-hash catalog embedding indexer (PLAN.md Phase 5). Reads ONLY the public product/category
 * endpoints (no credentials, via {@link PublicCatalogClient}); embeds via {@link EmbeddingProvider};
 * writes vectors via {@link ProductEmbeddingRepository}. Runs entirely on {@code aiIndexerScheduler}
 * (see {@link org.example.config.IndexerScheduleConfig}) — never a chat thread — and is its own
 * failure domain: any downstream failure is caught, recorded as a FAILURE run, and swallowed so the
 * scheduler keeps ticking and chat is completely unaffected (independent-failure-domain proof).
 *
 * <p><b>Exclusion of unapproved products.</b> {@code /api/v1/products/all} applies NO moderation or
 * active filter (§7 item 12, re-verified in source + live on {@code main}, 2026-07-11). Each item is
 * indexed only if {@link RemoteIndexProductDto#isPubliclyVisible()} — {@code status == "APPROVED" &&
 * isActive == true} — exactly product-service's own catalog-visibility predicate. A soft-deleted but
 * still-APPROVED product has {@code isActive=false} and is therefore excluded; a product that
 * disappears (or loses visibility) between runs has its stale embedding row deleted.
 */
@Slf4j
@Service
public class ProductIndexer {

    private static final String INDEX_LOCALE_HEADER = "RU"; // populate nameRu on the category list endpoint
    private static final String INDEX_LOCALE = "ru";        // and pick it via RemoteCategoryDto.displayName
    private static final int CATEGORY_PAGE_SIZE = 200;
    private static final int MAX_CATEGORY_PAGES = 20;

    private final PublicCatalogClient catalogClient;
    private final EmbeddingProvider embeddingProvider;
    private final EmbeddingTextBuilder textBuilder;
    private final ProductEmbeddingRepository embeddingRepository;
    private final IndexRunRepository indexRunRepository;
    private final ThreadPoolTaskScheduler indexerScheduler;

    private final boolean enabled;
    private final int pageSize;
    private final int maxPages;
    private final int embedBatchSize;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public ProductIndexer(
            PublicCatalogClient catalogClient,
            EmbeddingProvider embeddingProvider,
            EmbeddingTextBuilder textBuilder,
            ProductEmbeddingRepository embeddingRepository,
            IndexRunRepository indexRunRepository,
            @Qualifier("aiIndexerScheduler") ThreadPoolTaskScheduler indexerScheduler,
            @Value("${ai.indexer.enabled:true}") boolean enabled,
            @Value("${ai.indexer.page-size:50}") int pageSize,
            @Value("${ai.indexer.max-pages:1000}") int maxPages,
            @Value("${ai.embedding.batch-size:32}") int embedBatchSize) {
        this.catalogClient = catalogClient;
        this.embeddingProvider = embeddingProvider;
        this.textBuilder = textBuilder;
        this.embeddingRepository = embeddingRepository;
        this.indexRunRepository = indexRunRepository;
        this.indexerScheduler = indexerScheduler;
        this.enabled = enabled;
        this.pageSize = Math.max(pageSize, 1);
        this.maxPages = Math.max(maxPages, 1);
        this.embedBatchSize = Math.max(embedBatchSize, 1);
    }

    /**
     * Scheduled full pass. {@code fixedDelay} schedules the next run {@code interval} after this one
     * completes (never overlapping itself); {@link #running} additionally guards against an
     * admin-triggered run overlapping a scheduled one.
     */
    @Scheduled(initialDelayString = "${ai.indexer.initial-delay-ms:60000}", fixedDelayString = "${ai.indexer.interval-ms:1800000}")
    public void scheduledReindex() {
        if (!enabled) {
            return;
        }
        reindex("scheduled");
    }

    /**
     * Submit an admin-triggered reindex to run asynchronously on the indexer pool.
     * @return {@code true} if a run was started, {@code false} if one is already in progress.
     */
    public boolean triggerAsyncReindex() {
        // Reserve the run before returning to the controller. Checking running.get() and only
        // flipping it inside the queued task leaves a window where two admin requests both return
        // started=true and enqueue work.
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        try {
            indexerScheduler.execute(() -> runReserved("admin"));
            return true;
        } catch (RuntimeException schedulingFailure) {
            running.set(false);
            throw schedulingFailure;
        }
    }

    /** Run one full pass now, guarded so concurrent triggers don't overlap. Never throws. */
    public IndexOutcome reindex(String trigger) {
        if (!running.compareAndSet(false, true)) {
            log.info("Reindex ({}) skipped — a run is already in progress", trigger);
            return IndexOutcome.SKIPPED;
        }
        return runReserved(trigger);
    }

    /** Execute a run whose {@link #running} reservation has already been acquired. */
    private IndexOutcome runReserved(String trigger) {
        try {
            return doReindex(trigger);
        } finally {
            running.set(false);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public IndexStatus status() {
        IndexRun last = indexRunRepository.findFirstByOrderByLastRunAtDescIdDesc().orElse(null);
        return new IndexStatus(
                running.get(),
                embeddingRepository.count(),
                last == null ? null : last.getLastRunAt(),
                last == null ? "NEVER_RUN" : last.getLastStatus(),
                last == null ? null : last.getProductsIndexed(),
                last == null ? null : last.getNotes());
    }

    private IndexOutcome doReindex(String trigger) {
        Instant start = Instant.now();
        try {
            Map<Long, String> categoryNames = resolveCategoryNames();
            Map<Long, String> existingHashes = embeddingRepository.findAllContentHashes();
            Set<Long> seen = new HashSet<>();
            List<Pending> pending = new ArrayList<>();

            int pagesScanned = 0;
            int visible = 0;
            int rawItemsScanned = 0;
            Integer advertisedTotalPages = null;
            Long advertisedTotalElements = null;
            boolean pagingMetadataStable = true;
            boolean crawlComplete = false;
            Set<Long> crawledProductIds = new HashSet<>();
            for (int page = 1; page <= maxPages; page++) {
                RemoteProductListResponse response = catalogClient.fetchProductsPage(page, pageSize);
                if (response == null || response.items() == null) {
                    throw new IllegalStateException("Catalog returned an incomplete page " + page);
                }
                if (response.page() != null && response.page() != page) {
                    pagingMetadataStable = false;
                }
                if (response.totalPages() != null) {
                    if (response.totalPages() < 0) {
                        throw new IllegalStateException("Catalog returned invalid total_pages");
                    }
                    if (advertisedTotalPages != null && !advertisedTotalPages.equals(response.totalPages())) {
                        pagingMetadataStable = false;
                    }
                    advertisedTotalPages = response.totalPages();
                }
                if (response.totalElements() != null) {
                    if (response.totalElements() < 0) {
                        throw new IllegalStateException("Catalog returned invalid total_elements");
                    }
                    if (advertisedTotalElements != null && !advertisedTotalElements.equals(response.totalElements())) {
                        pagingMetadataStable = false;
                    }
                    advertisedTotalElements = response.totalElements();
                }
                if (response.items().isEmpty()) {
                    // An explicitly empty catalog is complete. Any early empty page in a non-empty
                    // advertised crawl is unsafe for stale deletion and is treated as a failed pass.
                    boolean hasEmptyMetadata = advertisedTotalPages != null || advertisedTotalElements != null;
                    boolean allSuppliedMetadataProvesEmpty = hasEmptyMetadata
                            && (advertisedTotalPages == null || advertisedTotalPages == 0)
                            && (advertisedTotalElements == null || advertisedTotalElements == 0);
                    if (page == 1 && allSuppliedMetadataProvesEmpty) {
                        crawlComplete = pagingMetadataStable;
                        break;
                    }
                    if (advertisedTotalPages == null && advertisedTotalElements == null) {
                        break; // no proof of completeness: keep all existing rows
                    }
                    throw new IllegalStateException("Catalog returned an empty page before the advertised end");
                }
                if (advertisedTotalPages != null && advertisedTotalPages == 0) {
                    throw new IllegalStateException("Catalog returned items while total_pages was zero");
                }
                if (advertisedTotalElements != null
                        && rawItemsScanned + response.items().size() > advertisedTotalElements) {
                    pagingMetadataStable = false;
                }
                rawItemsScanned += response.items().size();
                pagesScanned++;
                for (RemoteIndexProductDto item : response.items()) {
                    if (item.id() == null) {
                        pagingMetadataStable = false;
                        continue;
                    }
                    if (!crawledProductIds.add(item.id())) {
                        pagingMetadataStable = false;
                    }
                    if (!item.isPubliclyVisible()) {
                        continue;
                    }
                    visible++;
                    long id = item.id();
                    seen.add(id);
                    String categoryName = item.categoryId() == null ? null : categoryNames.get(item.categoryId());
                    String hash = textBuilder.contentHash(item, categoryName);
                    if (hash.equals(existingHashes.get(id))) {
                        continue; // unchanged — skip the embed call
                    }
                    pending.add(new Pending(item, textBuilder.buildText(item, categoryName), hash));
                }

                boolean reachedAdvertisedPages = advertisedTotalPages != null && page >= advertisedTotalPages;
                boolean reachedAdvertisedElements = advertisedTotalElements != null
                        && rawItemsScanned >= advertisedTotalElements;
                if (advertisedTotalPages != null && advertisedTotalElements != null) {
                    if (reachedAdvertisedPages || reachedAdvertisedElements) {
                        // When both signals exist, they must agree. Completing on either one alone
                        // could delete rows after a truncated page with inconsistent metadata.
                        crawlComplete = pagingMetadataStable
                                && reachedAdvertisedPages && reachedAdvertisedElements;
                        break;
                    }
                } else if (reachedAdvertisedPages || reachedAdvertisedElements) {
                    crawlComplete = pagingMetadataStable;
                    break;
                }
            }

            int embedded = 0;
            int failed = 0;
            for (int start0 = 0; start0 < pending.size(); start0 += embedBatchSize) {
                List<Pending> chunk = pending.subList(start0, Math.min(start0 + embedBatchSize, pending.size()));
                try {
                    List<float[]> vectors = embeddingProvider.embedDocuments(chunk.stream().map(Pending::text).toList());
                    if (vectors == null || vectors.size() != chunk.size()
                            || vectors.stream().anyMatch(vector -> vector == null)) {
                        int actual = vectors == null ? 0 : vectors.size();
                        throw new IllegalStateException(
                                "Embedding provider returned " + actual + " vectors for " + chunk.size() + " inputs");
                    }
                    for (int i = 0; i < chunk.size(); i++) {
                        embeddingRepository.upsert(toRow(chunk.get(i), vectors.get(i)));
                    }
                    embedded += chunk.size();
                } catch (RuntimeException e) {
                    // Isolate per-batch failures: this batch is left for the next run; the pass continues.
                    failed += chunk.size();
                    log.warn("Embedding batch failed ({} items), continuing: {}", chunk.size(), e.getMessage());
                }
            }

            Set<Long> toDelete = new HashSet<>(existingHashes.keySet());
            toDelete.removeAll(seen);
            int removed = crawlComplete ? embeddingRepository.deleteByProductIds(toDelete) : 0;

            long indexSize = embeddingRepository.count();
            IndexOutcome outcome = !crawlComplete ? IndexOutcome.PARTIAL
                    : failed == 0 ? IndexOutcome.SUCCESS
                    : (embedded > 0 ? IndexOutcome.PARTIAL : IndexOutcome.FAILURE);
            String notes = String.format(
                    "trigger=%s pages=%d crawlComplete=%s visible=%d changed=%d embedded=%d skippedUnchanged=%d failed=%d removed=%d staleDeletion=%s indexSize=%d durationMs=%d categoriesResolved=%d",
                    trigger, pagesScanned, crawlComplete, visible, pending.size(), embedded, visible - pending.size(),
                    failed, removed, crawlComplete ? "applied" : "skipped_incomplete_crawl", indexSize,
                    Duration.between(start, Instant.now()).toMillis(), categoryNames.size());
            recordRun(outcome.name(), (int) indexSize, notes);
            log.info("Reindex {} complete: {}", outcome, notes);
            return outcome;
        } catch (RuntimeException e) {
            // Whole-run failure (catalog unreachable, embed provider unconfigured, DB error, ...):
            // record it and swallow — the scheduler keeps ticking, chat is untouched.
            String notes = String.format("trigger=%s error=%s durationMs=%d",
                    trigger, e.getMessage(), Duration.between(start, Instant.now()).toMillis());
            recordRun(IndexOutcome.FAILURE.name(), 0, notes);
            log.warn("Reindex ({}) failed and was contained (chat unaffected): {}", trigger, e.getMessage());
            return IndexOutcome.FAILURE;
        }
    }

    private ProductEmbeddingRow toRow(Pending pending, float[] vector) {
        RemoteIndexProductDto p = pending.product();
        return new ProductEmbeddingRow(
                p.id(), p.slug(), p.name(), p.categoryId(), p.regionId(), p.price(), p.currency(),
                p.primaryImageUrl(), pending.hash(), vector);
    }

    /** Best-effort category id → localized name map; on failure returns empty so the run still proceeds. */
    private Map<Long, String> resolveCategoryNames() {
        Map<Long, String> names = new HashMap<>();
        try {
            for (int page = 0; page < MAX_CATEGORY_PAGES; page++) {
                RemoteSpringPage<RemoteCategoryDto> pageResult =
                        catalogClient.fetchCategories(page, CATEGORY_PAGE_SIZE, INDEX_LOCALE_HEADER);
                List<RemoteCategoryDto> content = pageResult == null ? null : pageResult.content();
                if (content == null || content.isEmpty()) {
                    break;
                }
                for (RemoteCategoryDto category : content) {
                    if (category.id() != null) {
                        names.put(category.id(), category.displayName(INDEX_LOCALE));
                    }
                }
                if (content.size() < CATEGORY_PAGE_SIZE) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            log.warn("Category name resolution failed; indexing without category names this run: {}", e.getMessage());
        }
        return names;
    }

    private void recordRun(String status, int productsIndexed, String notes) {
        try {
            IndexRun run = new IndexRun();
            run.setLastRunAt(Instant.now());
            run.setLastStatus(status);
            run.setProductsIndexed(productsIndexed);
            run.setNotes(notes);
            indexRunRepository.save(run);
        } catch (RuntimeException e) {
            log.warn("Failed to persist index_state row: {}", e.getMessage());
        }
    }

    public enum IndexOutcome {
        SUCCESS, PARTIAL, FAILURE, SKIPPED
    }

    private record Pending(RemoteIndexProductDto product, String text, String hash) {
    }
}
