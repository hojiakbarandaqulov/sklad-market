package org.example.ai.business.service;

import org.example.ai.business.dto.BusinessContactLookup;
import org.example.ai.business.dto.BusinessContactStatus;
import org.example.ai.business.dto.BusinessIndexFreshness;
import org.example.ai.business.dto.SupplierRecommendation;
import org.example.ai.business.dto.SupplierRecommendationResponse;
import org.example.ai.business.index.BusinessLexicalRepository;
import org.example.ai.business.index.CompanyEmbeddingRepository;
import org.example.ai.business.index.CompanySearchHit;
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
public class SupplierRecommendationService {

    private final BuyerPreferenceService preferences;
    private final BusinessQueryEmbeddingService queryEmbeddings;
    private final CompanyEmbeddingRepository companies;
    private final BusinessLexicalRepository lexical;
    private final PublicCompanyContactHydrator contacts;
    private final BusinessIndexFreshnessService freshness;

    public SupplierRecommendationService(
            BuyerPreferenceService preferences,
            BusinessQueryEmbeddingService queryEmbeddings,
            CompanyEmbeddingRepository companies,
            BusinessLexicalRepository lexical,
            PublicCompanyContactHydrator contacts,
            BusinessIndexFreshnessService freshness) {
        this.preferences = preferences;
        this.queryEmbeddings = queryEmbeddings;
        this.companies = companies;
        this.lexical = lexical;
        this.contacts = contacts;
        this.freshness = freshness;
    }

    public SupplierRecommendationResponse recommend(
            String explicitNeed,
            Long categoryId,
            Long regionId,
            Double minPrice,
            Double maxPrice,
            Integer requestedLimit,
            ToolExecutionContext context) {
        rejectCrossCurrencyPriceFilter(minPrice, maxPrice);
        int limit = Math.max(1, Math.min(requestedLimit == null ? 8 : requestedLimit, 20));
        BuyerPreferenceService.Preference preference = preferences.resolve(explicitNeed, context);
        float[] vector = optionalEmbedding(preference.text());

        List<CompanySearchHit> semantic = vector == null ? List.of()
                : companies.searchSuppliers(vector, categoryId, regionId, 150);
        List<CompanySearchHit> nameMatches = lexical.searchCompanies(
                preference.text(), categoryId, regionId, true, 150);
        Map<Long, CompanySearchHit> semanticById = new LinkedHashMap<>();
        Map<Long, CompanySearchHit> lexicalById = new LinkedHashMap<>();
        semantic.forEach(hit -> semanticById.putIfAbsent(hit.companyId(), hit));
        nameMatches.forEach(hit -> lexicalById.putIfAbsent(hit.companyId(), hit));
        Set<Long> ids = new LinkedHashSet<>(semanticById.keySet());
        ids.addAll(lexicalById.keySet());

        List<SupplierRecommendation> selectedWithoutContacts = ids.stream()
                .map(id -> {
                    CompanySearchHit semanticHit = semanticById.get(id);
                    CompanySearchHit lexicalHit = lexicalById.get(id);
                    CompanySearchHit hit = semanticHit != null ? semanticHit : lexicalHit;
                    return score(hit, categoryId, regionId, preference.source(),
                            semanticHit == null ? null : semanticHit.score(),
                            lexicalHit == null ? null : lexicalHit.score());
                })
                .sorted(Comparator.comparingDouble(SupplierRecommendation::relevance).reversed()
                        .thenComparing(SupplierRecommendation::companyId))
                .limit(limit)
                .toList();
        Map<String, BusinessContactLookup> publicContacts = contacts.hydrateBatch(
                selectedWithoutContacts.stream().map(SupplierRecommendation::slug).toList(), context);
        if (publicContacts == null) publicContacts = Map.of();
        Map<String, BusinessContactLookup> finalContacts = publicContacts;
        List<SupplierRecommendation> ranked = selectedWithoutContacts.stream()
                .map(item -> enrichContact(item, finalContacts))
                .toList();
        BusinessIndexFreshness indexFreshness = freshness.snapshot(false, true);

        return new SupplierRecommendationResponse(
                preference.source(), ranked.size(), ranked, BusinessSearchService.SCORE_MEANING,
                "Recommendations are indexed catalog matches, not guaranteed commercial outcomes. "
                        + "Supplier-level price ranges are omitted because products may use different currencies; "
                        + "compare individual product prices and currencies.",
                indexFreshness);
    }

    private SupplierRecommendation score(
            CompanySearchHit hit,
            Long categoryId,
            Long regionId,
            String source,
            Double semanticScore,
            Double lexicalScore) {
        double relevance = fusedScore(semanticScore, lexicalScore);
        double category = categoryId == null ? 0.5 : 1.0;
        double region = regionId == null ? 0.5 : 1.0;
        double trust = "VERIFIED".equals(hit.verificationStatus()) ? 1.0 : 0.0;
        double quality = Math.min(1.0, Math.log1p(hit.productCount()) / Math.log(21.0));
        double total;
        if ("COLD_START".equals(source)) {
            total = relevance * 0.30 + category * 0.20 + region * 0.10
                    + trust * 0.25 + quality * 0.15;
        } else {
            total = relevance * 0.58 + category * 0.20 + region * 0.10
                    + trust * 0.08 + quality * 0.04;
        }

        List<String> reasons = new ArrayList<>();
        if ("EXPLICIT".equals(source)) {
            reasons.add("MATCHED_EXPLICIT_NEED");
        } else if ("OWN_ACTIVITY".equals(source)) {
            reasons.add("OWN_ACTIVITY_RELEVANCE");
        } else {
            reasons.add("GENERAL_CATALOG_RELEVANCE");
        }
        if (semanticScore != null) reasons.add("SEMANTIC_OFFERING_RELEVANCE");
        if (lexicalScore != null) reasons.add("LEXICAL_NAME_OR_SLUG_MATCH");
        if (categoryId != null) reasons.add("CATEGORY_MATCH");
        if (regionId != null) reasons.add("REGION_MATCH");
        if (trust == 1.0) reasons.add("INDEXED_AS_VERIFIED");
        reasons.add("INDEXED_PUBLIC_CATALOG");
        return new SupplierRecommendation(hit.companyId(), hit.slug(), hit.name(), hit.logoUrl(), hit.verificationStatus(),
                hit.categoryIds(), hit.regionIds(), hit.productCount(), null, null,
                round(total), List.copyOf(reasons), BusinessContactStatus.NOT_CHECKED, null);
    }

    private SupplierRecommendation enrichContact(
            SupplierRecommendation item,
            Map<String, BusinessContactLookup> publicContacts) {
        BusinessContactLookup lookup = publicContacts.getOrDefault(
                item.slug(), BusinessContactLookup.status(BusinessContactStatus.NOT_CHECKED));
        return new SupplierRecommendation(item.companyId(), item.slug(), item.name(), item.logoUrl(), item.verificationStatus(),
                item.categoryIds(), item.regionIds(), item.productCount(), null, null,
                item.relevance(), item.reasons(), lookup.status(), lookup.contact());
    }

    private void rejectCrossCurrencyPriceFilter(Double minPrice, Double maxPrice) {
        if (minPrice != null || maxPrice != null) {
            throw new IllegalArgumentException(
                    "Supplier price filtering is unavailable because a supplier catalog can contain multiple currencies; "
                            + "search and compare individual products instead");
        }
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
            return Math.max(semanticValue * 0.65 + lexicalValue * 0.35,
                    Math.max(semanticValue, lexicalValue) * 0.90);
        }
        return (semantic != null ? semanticValue : lexicalValue) * 0.90;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }
}
