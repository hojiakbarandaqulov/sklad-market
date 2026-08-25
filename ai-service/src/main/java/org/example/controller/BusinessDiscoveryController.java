package org.example.controller;

import org.example.ai.LocaleNormalizer;
import org.example.ai.business.dto.BusinessResultType;
import org.example.ai.business.dto.BusinessSearchCriteria;
import org.example.ai.business.dto.BusinessSearchResponse;
import org.example.ai.business.dto.SupplierRecommendationRequest;
import org.example.ai.business.dto.SupplierRecommendationResponse;
import org.example.ai.business.service.BusinessSearchService;
import org.example.ai.business.service.SupplierRecommendationService;
import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.ai.guardrail.RpmRateLimiter;
import org.example.ai.guardrail.AiQuotaProtected;
import org.example.ai.guardrail.TokenBudgetGuard;
import org.example.ai.guardrail.UsageLedgerService;
import org.example.ai.observability.AiMetrics;
import org.example.ai.tool.CategoryResolver;
import org.example.ai.tool.ToolExecutionContext;
import org.example.dto.ApiResponse;
import org.example.security.AiSecurityUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/ai")
public class BusinessDiscoveryController {

    private final BusinessSearchService searchService;
    private final SupplierRecommendationService supplierService;
    private final CategoryResolver categoryResolver;
    private final RpmRateLimiter rateLimiter;
    private final TokenBudgetGuard budgetGuard;
    private final UsageLedgerService usageLedgerService;
    private final AiMetrics metrics;

    public BusinessDiscoveryController(
            BusinessSearchService searchService,
            SupplierRecommendationService supplierService,
            CategoryResolver categoryResolver,
            RpmRateLimiter rateLimiter,
            TokenBudgetGuard budgetGuard,
            UsageLedgerService usageLedgerService,
            AiMetrics metrics) {
        this.searchService = searchService;
        this.supplierService = supplierService;
        this.categoryResolver = categoryResolver;
        this.rateLimiter = rateLimiter;
        this.budgetGuard = budgetGuard;
        this.usageLedgerService = usageLedgerService;
        this.metrics = metrics;
    }

    @GetMapping("/business-search")
    @AiQuotaProtected
    public ApiResponse<BusinessSearchResponse> search(
            @RequestParam("q") String query,
            @RequestParam(value = "types", required = false) String rawTypes,
            @RequestParam(value = "categorySlug", required = false) String categorySlug,
            @RequestParam(value = "regionId", required = false) Long regionId,
            @RequestParam(value = "minPrice", required = false) Double minPrice,
            @RequestParam(value = "maxPrice", required = false) Double maxPrice,
            @RequestParam(value = "currency", required = false) String currency,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestHeader(value = "Accept-Language", required = false) String language) {
        ToolExecutionContext context = currentContext(language);
        Long categoryId = resolveCategory(categorySlug, context);
        long started = System.currentTimeMillis();
        try {
            BusinessSearchResponse response = searchService.search(new BusinessSearchCriteria(
                    query, parseTypes(rawTypes), categoryId, regionId, minPrice, maxPrice, currency, limit),
                    context, true);
            metrics.recordBusinessDiscovery("business-search", "ok", System.currentTimeMillis() - started);
            return ApiResponse.successResponse(response);
        } catch (RuntimeException e) {
            metrics.recordBusinessDiscovery("business-search", "error", System.currentTimeMillis() - started);
            throw e;
        }
    }

    @PostMapping("/recommendations/suppliers")
    @PreAuthorize("hasRole('BUYER')")
    @AiQuotaProtected
    public ApiResponse<SupplierRecommendationResponse> recommendSuppliers(
            @RequestBody(required = false) SupplierRecommendationRequest request,
            @RequestHeader(value = "Accept-Language", required = false) String language) {
        SupplierRecommendationRequest safe = request == null
                ? new SupplierRecommendationRequest(null, null, null, null)
                : request;
        ToolExecutionContext context = currentContext(language);
        guard("recommend-suppliers", context.userSub());
        Long categoryId = resolveCategory(safe.categorySlug(), context);
        long started = System.currentTimeMillis();
        try {
            SupplierRecommendationResponse response = supplierService.recommend(
                    safe.need(), categoryId, safe.regionId(), null, null, safe.limit(), context);
            usageLedgerService.recordEmbeddingRequest(context.userSub(), safe.need());
            metrics.recordBusinessDiscovery("recommend-suppliers", "ok", System.currentTimeMillis() - started);
            return ApiResponse.successResponse(response);
        } catch (RuntimeException e) {
            metrics.recordBusinessDiscovery("recommend-suppliers", "error", System.currentTimeMillis() - started);
            throw e;
        }
    }

    private ToolExecutionContext currentContext(String language) {
        return new ToolExecutionContext(null, AiSecurityUtil.requireSub(), AiSecurityUtil.requireBearerToken(),
                AiSecurityUtil.currentRoleSet(), LocaleNormalizer.normalize(language));
    }

    private Long resolveCategory(String slug, ToolExecutionContext context) {
        if (slug == null || slug.isBlank()) return null;
        return categoryResolver.resolve(slug, context)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + slug));
    }

    private Set<BusinessResultType> parseTypes(String rawTypes) {
        if (rawTypes == null || rawTypes.isBlank()) return EnumSet.allOf(BusinessResultType.class);
        EnumSet<BusinessResultType> types = EnumSet.noneOf(BusinessResultType.class);
        for (String value : rawTypes.split(",")) {
            try {
                types.add(BusinessResultType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unsupported business result type: " + value);
            }
        }
        if (types.isEmpty()) throw new IllegalArgumentException("At least one result type is required");
        return types;
    }

    private void guard(String operation, String userSub) {
        if (!rateLimiter.tryConsume("rest-" + operation + ":" + userSub)) {
            throw new AiChatException(AiErrorCode.RATE_LIMITED, "Too many requests, please slow down.");
        }
        if (!budgetGuard.hasRemainingBudget(userSub)) {
            throw new AiChatException(AiErrorCode.BUDGET_EXCEEDED, "Daily usage limit reached.");
        }
    }

}
