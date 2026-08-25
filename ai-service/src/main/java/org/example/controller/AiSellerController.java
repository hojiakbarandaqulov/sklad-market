package org.example.controller;

import org.example.ai.guardrail.AiQuotaProtected;
import org.example.dto.ApiResponse;
import org.example.dto.SuggestListingRequest;
import org.example.dto.SuggestListingResponse;
import org.example.security.AiSecurityUtil;
import org.example.service.SellerListingSuggestionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seller-only listing assist (PLAN.md Phase 6, C8). Dual-gated like {@link AiAdminController}: the
 * gateway route requires a valid JWT, and {@code @PreAuthorize} additionally requires
 * {@code ROLE_SELLER} (401 unauthenticated / 403 non-seller / 200 seller — the test matrix). A
 * plain synchronous JSON endpoint, not SSE — this is a one-shot suggestion, not a chat turn.
 */
@RestController
@RequestMapping("/api/v1/ai/seller")
public class AiSellerController {

    private final SellerListingSuggestionService suggestionService;

    public AiSellerController(SellerListingSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @PostMapping("/suggest-listing")
    @PreAuthorize("hasRole('SELLER')")
    @AiQuotaProtected
    public ApiResponse<SuggestListingResponse> suggestListing(
            @RequestBody SuggestListingRequest request,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) {
        String userSub = AiSecurityUtil.requireSub();
        String bearerToken = AiSecurityUtil.requireBearerToken();
        return ApiResponse.successResponse(suggestionService.suggest(userSub, bearerToken, acceptLanguage, request));
    }
}
