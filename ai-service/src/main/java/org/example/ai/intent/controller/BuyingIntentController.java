package org.example.ai.intent.controller;

import jakarta.validation.Valid;
import org.example.ai.intent.dto.BuyingIntentMatchResult;
import org.example.ai.intent.dto.BuyingIntentPublicationRequest;
import org.example.ai.intent.dto.BuyingIntentRequest;
import org.example.ai.intent.dto.BuyingIntentResponse;
import org.example.ai.intent.service.BuyingIntentService;
import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.ai.guardrail.RpmRateLimiter;
import org.example.dto.ApiResponse;
import org.example.dto.PagedResponse;
import org.example.security.AiSecurityUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/buying-intents")
public class BuyingIntentController {

    private final BuyingIntentService service;
    private final RpmRateLimiter rateLimiter;

    public BuyingIntentController(BuyingIntentService service, RpmRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<BuyingIntentResponse> createDraft(@Valid @RequestBody BuyingIntentRequest request) {
        String ownerSub = AiSecurityUtil.requireSub();
        guard("write", ownerSub);
        return ApiResponse.successResponse(service.createDraft(ownerSub, request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<BuyingIntentResponse> updateDraft(
            @PathVariable UUID id, @Valid @RequestBody BuyingIntentRequest request) {
        String ownerSub = AiSecurityUtil.requireSub();
        guard("write", ownerSub);
        return ApiResponse.successResponse(service.updateDraft(ownerSub, id, request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<BuyingIntentResponse> publish(
            @PathVariable UUID id,
            @Valid @RequestBody BuyingIntentPublicationRequest request) {
        String ownerSub = AiSecurityUtil.requireSub();
        guard("write", ownerSub);
        return ApiResponse.successResponse(service.publish(ownerSub, id, Boolean.TRUE.equals(request.publicationConsent())));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<BuyingIntentResponse> close(@PathVariable UUID id) {
        String ownerSub = AiSecurityUtil.requireSub();
        guard("write", ownerSub);
        return ApiResponse.successResponse(service.close(ownerSub, id));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<PagedResponse<BuyingIntentResponse>> mine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestParam(required = false) String status) {
        return ApiResponse.successResponse(service.listOwn(AiSecurityUtil.requireSub(), page, perPage, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<BuyingIntentResponse> getOwn(@PathVariable UUID id) {
        return ApiResponse.successResponse(service.getOwn(AiSecurityUtil.requireSub(), id));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<BuyingIntentMatchResult> search(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String region,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) Integer limit) {
        String sellerSub = AiSecurityUtil.requireSub();
        guard("search", sellerSub);
        return ApiResponse.successResponse(service.searchPublished(category, region, query, limit));
    }

    private void guard(String operation, String userSub) {
        if (!rateLimiter.tryConsume("buying-intent-" + operation + ":" + userSub)) {
            throw new AiChatException(AiErrorCode.RATE_LIMITED, "Too many buying-intent requests, please slow down.");
        }
    }
}
