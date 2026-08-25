package org.example.controller;

import org.example.ai.embedding.EmbeddingSearchService;
import org.example.ai.guardrail.AiQuotaProtected;
import org.example.ai.observability.AiMetrics;
import org.example.dto.ApiResponse;
import org.example.dto.SearchResultItem;
import org.example.dto.SemanticSearchResponse;
import org.example.dto.SimilarProductsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vector-index read endpoints (PLAN.md Phase 5). Both are authenticated (SecurityConfig's
 * {@code anyRequest().authenticated()}), owner-agnostic (the catalog is public data), and never
 * call downstream services — they query the local {@code product_embedding} table only. The
 * The frontend product page consumes the "similar products" endpoint for logged-in users and
 * retains its ordinary same-category list as a failure-safe fallback.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiSearchController {

    private final EmbeddingSearchService searchService;
    private final AiMetrics metrics;

    public AiSearchController(
            EmbeddingSearchService searchService,
            AiMetrics metrics) {
        this.searchService = searchService;
        this.metrics = metrics;
    }

    @GetMapping("/search")
    @AiQuotaProtected
    public ApiResponse<SemanticSearchResponse> search(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", required = false) Integer limit) {
        long startedAt = System.currentTimeMillis();
        try {
            List<SearchResultItem> items = searchService.search(query, limit);
            metrics.recordSemanticSearch("search", "ok", System.currentTimeMillis() - startedAt);
            return ApiResponse.successResponse(new SemanticSearchResponse(query, items.size(), items));
        } catch (RuntimeException e) {
            metrics.recordSemanticSearch("search", "error", System.currentTimeMillis() - startedAt);
            throw e;
        }
    }

    @GetMapping("/similar/{productId}")
    @AiQuotaProtected
    public ApiResponse<SimilarProductsResponse> similar(
            @PathVariable long productId,
            @RequestParam(value = "limit", required = false) Integer limit) {
        long startedAt = System.currentTimeMillis();
        try {
            List<SearchResultItem> items = searchService.similar(productId, limit);
            metrics.recordSemanticSearch("similar", "ok", System.currentTimeMillis() - startedAt);
            return ApiResponse.successResponse(new SimilarProductsResponse(productId, items.size(), items));
        } catch (RuntimeException e) {
            metrics.recordSemanticSearch("similar", "error", System.currentTimeMillis() - startedAt);
            throw e;
        }
    }

}
