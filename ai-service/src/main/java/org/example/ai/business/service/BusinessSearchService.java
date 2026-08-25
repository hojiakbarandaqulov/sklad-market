package org.example.ai.business.service;

import org.example.ai.business.dto.BusinessContactLookup;
import org.example.ai.business.dto.BusinessContactStatus;
import org.example.ai.business.dto.BusinessIndexFreshness;
import org.example.ai.business.dto.BusinessResultType;
import org.example.ai.business.dto.BusinessSearchCriteria;
import org.example.ai.business.dto.BusinessSearchItem;
import org.example.ai.business.dto.BusinessSearchResponse;
import org.example.ai.business.index.BusinessLexicalRepository;
import org.example.ai.business.index.CompanyEmbeddingRepository;
import org.example.ai.business.index.CompanySearchHit;
import org.example.ai.business.remote.PublicBusinessClient;
import org.example.ai.business.remote.RemoteBusinessProduct;
import org.example.ai.business.remote.RemoteBusinessProductPage;
import org.example.ai.business.remote.RemoteCompanyPage;
import org.example.ai.business.remote.RemotePublicCompany;
import org.example.ai.embedding.EmbeddingSearchHit;
import org.example.ai.embedding.ProductEmbeddingRepository;
import org.example.ai.tool.ToolExecutionContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BusinessSearchService {

    public static final String SCORE_MEANING =
            "Relevance fuses semantic and local name/slug signals; it is not a guarantee. "
                    + "When the AI index is still warming up, verified live catalog matches may be used. "
                    + "Price filters require a currency and apply only to individual product prices, "
                    + "never to cross-currency company catalog totals.";

    private final BusinessQueryEmbeddingService queryEmbeddings;
    private final ProductEmbeddingRepository products;
    private final CompanyEmbeddingRepository companies;
    private final BusinessLexicalRepository lexical;
    private final PublicBusinessClient liveCatalog;
    private final PublicCompanyContactHydrator contacts;
    private final BusinessIndexFreshnessService freshness;

    public BusinessSearchService(
            BusinessQueryEmbeddingService queryEmbeddings,
            ProductEmbeddingRepository products,
            CompanyEmbeddingRepository companies,
            BusinessLexicalRepository lexical,
            PublicBusinessClient liveCatalog,
            PublicCompanyContactHydrator contacts,
            BusinessIndexFreshnessService freshness) {
        this.queryEmbeddings = queryEmbeddings;
        this.products = products;
        this.companies = companies;
        this.lexical = lexical;
        this.liveCatalog = liveCatalog;
        this.contacts = contacts;
        this.freshness = freshness;
    }

    public BusinessSearchResponse search(BusinessSearchCriteria criteria, ToolExecutionContext context) {
        return search(criteria, context, true);
    }

    /**
     * Dashboard REST search can disable the paid semantic leg after a user's daily AI budget is
     * exhausted. Local lexical and live public-catalog discovery still work instead of returning
     * a misleading rate-limit screen for a free fallback path.
     */
    public BusinessSearchResponse search(
            BusinessSearchCriteria criteria, ToolExecutionContext context, boolean semanticEnabled) {
        float[] vector = semanticEnabled ? optionalEmbedding(criteria.query()) : null;
        int candidates = Math.min(200, Math.max(50, criteria.limit() * 12));
        List<BusinessSearchItem> ranked = new ArrayList<>();

        if (criteria.types().contains(BusinessResultType.PRODUCT)) {
            List<EmbeddingSearchHit> semantic = vector == null ? List.of()
                    : products.searchFiltered(vector, criteria.categoryId(), criteria.regionId(),
                    criteria.minPrice(), criteria.maxPrice(), criteria.currency(), candidates);
            List<EmbeddingSearchHit> nameMatches = lexical.searchProducts(criteria.query(), criteria.categoryId(),
                    criteria.regionId(), criteria.minPrice(), criteria.maxPrice(), criteria.currency(), candidates);
            List<BusinessSearchItem> productMatches = productItems(semantic, nameMatches, criteria);
            if (productMatches.isEmpty() && supportsLiveFallback(criteria)) {
                productMatches = liveProductItems(criteria);
            }
            ranked.addAll(productMatches);
        }

        if (criteria.types().contains(BusinessResultType.COMPANY)) {
            // Company price aggregates are intentionally not filtered: a company can list products
            // in different currencies, so one min/max range would be economically meaningless.
            List<CompanySearchHit> semantic = vector == null ? List.of()
                    : companies.searchFiltered(vector, criteria.categoryId(), criteria.regionId(), candidates);
            List<CompanySearchHit> nameMatches = lexical.searchCompanies(criteria.query(), criteria.categoryId(),
                    criteria.regionId(), false, candidates);
            List<BusinessSearchItem> companyMatches = companyItems(semantic, nameMatches, criteria);
            if (companyMatches.isEmpty() && supportsLiveFallback(criteria)) {
                companyMatches = liveCompanyItems(criteria);
            }
            ranked.addAll(companyMatches);
        }

        List<BusinessSearchItem> selectedWithoutContacts = ranked.stream()
                .sorted(Comparator.comparingDouble(BusinessSearchItem::relevance).reversed()
                        .thenComparing(item -> item.type().name())
                        .thenComparing(BusinessSearchItem::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .limit(criteria.limit())
                .toList();
        List<String> companySlugs = selectedWithoutContacts.stream()
                .filter(item -> item.type() == BusinessResultType.COMPANY)
                .map(BusinessSearchItem::slug)
                .toList();
        Map<String, BusinessContactLookup> publicContacts = contacts.hydrateBatch(companySlugs, context);
        if (publicContacts == null) publicContacts = Map.of();
        Map<String, BusinessContactLookup> finalContacts = publicContacts;
        List<BusinessSearchItem> selected = selectedWithoutContacts.stream()
                .map(item -> enrich(item, finalContacts))
                .toList();
        BusinessIndexFreshness indexFreshness = freshness.snapshot(
                criteria.types().contains(BusinessResultType.PRODUCT),
                criteria.types().contains(BusinessResultType.COMPANY));
        return new BusinessSearchResponse(criteria.query(), selected.size(), selected, SCORE_MEANING, indexFreshness);
    }

    private boolean supportsLiveFallback(BusinessSearchCriteria criteria) {
        // The public fallback endpoints cannot faithfully apply every AI capability filter. Never
        // return a page-filtered approximation when the caller asked for a structured filter.
        return criteria.categoryId() == null && criteria.regionId() == null
                && criteria.minPrice() == null && criteria.maxPrice() == null;
    }

    private List<BusinessSearchItem> liveProductItems(BusinessSearchCriteria criteria) {
        try {
            RemoteBusinessProductPage page = liveCatalog.searchPublicProducts(
                    criteria.query(), Math.min(50, Math.max(12, criteria.limit() * 3)));
            if (page == null || page.items() == null) return List.of();
            return page.items().stream()
                    .filter(RemoteBusinessProduct::publiclyVisible)
                    .limit(criteria.limit())
                    .map(item -> new BusinessSearchItem(
                            BusinessResultType.PRODUCT, item.id(), item.slug(), item.name(), item.primaryImageUrl(), null,
                            item.categoryId(),
                            item.regionId(), item.price(), item.currency(), null, List.of(), List.of(), null,
                            null, null, 0.70, List.of("LIVE_CATALOG_MATCH"), null, null))
                    .toList();
        } catch (RuntimeException unavailable) {
            return List.of();
        }
    }

    private List<BusinessSearchItem> liveCompanyItems(BusinessSearchCriteria criteria) {
        try {
            RemoteCompanyPage page = liveCatalog.searchVerifiedCompanies(
                    criteria.query(), Math.min(50, Math.max(12, criteria.limit() * 3)));
            if (page == null || page.content() == null) return List.of();
            return page.content().stream()
                    .filter(RemotePublicCompany::indexable)
                    .limit(criteria.limit())
                    .map(item -> new BusinessSearchItem(
                            BusinessResultType.COMPANY, item.id(), item.slug(), item.name(), null, item.logoUrl(), null, null,
                            null, null, item.verificationStatus(), List.of(), List.of(), null,
                            null, null, 0.70, List.of("LIVE_COMPANY_MATCH", "LIVE_AS_VERIFIED"),
                            BusinessContactStatus.NOT_CHECKED, null))
                    .toList();
        } catch (RuntimeException unavailable) {
            return List.of();
        }
    }

    private List<BusinessSearchItem> productItems(
            List<EmbeddingSearchHit> semantic,
            List<EmbeddingSearchHit> lexicalHits,
            BusinessSearchCriteria criteria) {
        Map<Long, EmbeddingSearchHit> semanticById = new LinkedHashMap<>();
        Map<Long, EmbeddingSearchHit> lexicalById = new LinkedHashMap<>();
        semantic.forEach(hit -> semanticById.putIfAbsent(hit.productId(), hit));
        lexicalHits.forEach(hit -> lexicalById.putIfAbsent(hit.productId(), hit));
        Set<Long> ids = new LinkedHashSet<>(semanticById.keySet());
        ids.addAll(lexicalById.keySet());
        List<BusinessSearchItem> items = new ArrayList<>(ids.size());
        for (Long id : ids) {
            EmbeddingSearchHit semanticHit = semanticById.get(id);
            EmbeddingSearchHit lexicalHit = lexicalById.get(id);
            EmbeddingSearchHit hit = semanticHit != null ? semanticHit : lexicalHit;
            items.add(new BusinessSearchItem(
                    BusinessResultType.PRODUCT, hit.productId(), hit.slug(), hit.name(), hit.imageUrl(), null,
                    hit.categoryId(),
                    hit.regionId(), hit.price(), hit.currency(), null, List.of(), List.of(), null,
                    null, null, fusedScore(semanticHit == null ? null : semanticHit.score(),
                    lexicalHit == null ? null : lexicalHit.score()),
                    productReasons(hit, criteria, semanticHit != null, lexicalHit != null), null, null));
        }
        return items;
    }

    private List<BusinessSearchItem> companyItems(
            List<CompanySearchHit> semantic,
            List<CompanySearchHit> lexicalHits,
            BusinessSearchCriteria criteria) {
        Map<Long, CompanySearchHit> semanticById = new LinkedHashMap<>();
        Map<Long, CompanySearchHit> lexicalById = new LinkedHashMap<>();
        semantic.forEach(hit -> semanticById.putIfAbsent(hit.companyId(), hit));
        lexicalHits.forEach(hit -> lexicalById.putIfAbsent(hit.companyId(), hit));
        Set<Long> ids = new LinkedHashSet<>(semanticById.keySet());
        ids.addAll(lexicalById.keySet());
        List<BusinessSearchItem> items = new ArrayList<>(ids.size());
        for (Long id : ids) {
            CompanySearchHit semanticHit = semanticById.get(id);
            CompanySearchHit lexicalHit = lexicalById.get(id);
            CompanySearchHit hit = semanticHit != null ? semanticHit : lexicalHit;
            items.add(new BusinessSearchItem(
                    BusinessResultType.COMPANY, hit.companyId(), hit.slug(), hit.name(), null, hit.logoUrl(), null, null,
                    null, null, hit.verificationStatus(), hit.categoryIds(), hit.regionIds(), hit.productCount(),
                    null, null, fusedScore(semanticHit == null ? null : semanticHit.score(),
                    lexicalHit == null ? null : lexicalHit.score()),
                    companyReasons(hit, criteria, semanticHit != null, lexicalHit != null),
                    BusinessContactStatus.NOT_CHECKED, null));
        }
        return items;
    }

    private BusinessSearchItem enrich(
            BusinessSearchItem item, Map<String, BusinessContactLookup> publicContacts) {
        if (item.type() != BusinessResultType.COMPANY) return item;
        BusinessContactLookup lookup = publicContacts.getOrDefault(
                item.slug(), BusinessContactLookup.status(BusinessContactStatus.NOT_CHECKED));
        return new BusinessSearchItem(item.type(), item.id(), item.slug(), item.name(), item.imageUrl(), item.logoUrl(),
                item.categoryId(), item.regionId(), item.price(), item.currency(), item.verificationStatus(), item.categoryIds(),
                item.regionIds(), item.productCount(), null, null, item.relevance(), item.reasons(),
                lookup.status(), lookup.contact());
    }

    private List<String> productReasons(
            EmbeddingSearchHit hit,
            BusinessSearchCriteria criteria,
            boolean semanticMatch,
            boolean lexicalMatch) {
        List<String> reasons = new ArrayList<>();
        if (semanticMatch) reasons.add("SEMANTIC_MATCH");
        if (lexicalMatch) reasons.add("LEXICAL_NAME_OR_SLUG_MATCH");
        if (criteria.categoryId() != null && criteria.categoryId().equals(hit.categoryId())) reasons.add("CATEGORY_MATCH");
        if (criteria.regionId() != null && criteria.regionId().equals(hit.regionId())) reasons.add("REGION_MATCH");
        if (criteria.minPrice() != null || criteria.maxPrice() != null) reasons.add("PRODUCT_PRICE_FILTER_MATCH");
        return List.copyOf(reasons);
    }

    private List<String> companyReasons(
            CompanySearchHit hit,
            BusinessSearchCriteria criteria,
            boolean semanticMatch,
            boolean lexicalMatch) {
        List<String> reasons = new ArrayList<>();
        if (semanticMatch) reasons.add("SEMANTIC_OFFERING_RELEVANCE");
        if (lexicalMatch) reasons.add("LEXICAL_NAME_OR_SLUG_MATCH");
        if (criteria.categoryId() != null && hit.categoryIds().contains(criteria.categoryId())) reasons.add("CATEGORY_MATCH");
        if (criteria.regionId() != null && hit.regionIds().contains(criteria.regionId())) reasons.add("REGION_MATCH");
        if ("VERIFIED".equals(hit.verificationStatus())) reasons.add("INDEXED_AS_VERIFIED");
        if (hit.productCount() > 0) reasons.add("INDEXED_PUBLIC_CATALOG");
        return List.copyOf(reasons);
    }

    private float[] optionalEmbedding(String query) {
        try {
            return queryEmbeddings.embed(query);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    private double fusedScore(Double semantic, Double lexicalScore) {
        double semanticValue = semantic == null ? 0.0 : clamp(semantic);
        double lexicalValue = lexicalScore == null ? 0.0 : clamp(lexicalScore);
        if (semantic != null && lexicalScore != null) {
            return round(Math.max(semanticValue * 0.65 + lexicalValue * 0.35,
                    Math.max(semanticValue, lexicalValue) * 0.90));
        }
        return round((semantic != null ? semanticValue : lexicalValue) * 0.90);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
