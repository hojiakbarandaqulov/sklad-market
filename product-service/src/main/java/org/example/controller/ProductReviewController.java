package org.example.controller;

import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.review.ProductReviewCreateRequest;
import org.example.dto.review.ProductReviewListResponse;
import org.example.dto.review.ProductReviewResponse;
import org.example.dto.review.ProductReviewUpdateRequest;
import org.example.enums.AppLanguage;
import org.example.service.ProductReviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductReviewController {

    private final ProductReviewService productReviewService;

    @PermitAll
    @GetMapping("/{productId}/reviews")
    public ApiResponse<ProductReviewListResponse> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return ApiResponse.successResponse(
                productReviewService.getProductReviews(productId, page, perPage, language)
        );
    }

    @PermitAll
    @GetMapping("/reviews/company/{companyId}")
    public ApiResponse<ProductReviewListResponse> getCompanyProductReviews(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return ApiResponse.successResponse(
                productReviewService.getCompanyProductReviews(companyId, page, perPage, language)
        );
    }

    @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    @PostMapping("/{productId}/reviews")
    public ApiResponse<ProductReviewResponse> create(
            @PathVariable Long productId,
            @RequestBody @Valid ProductReviewCreateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return ApiResponse.successResponse(productReviewService.create(productId, request, language));
    }

    @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    @PutMapping("/{productId}/reviews/{reviewId}")
    public ApiResponse<ProductReviewResponse> update(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @RequestBody @Valid ProductReviewUpdateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return ApiResponse.successResponse(
                productReviewService.update(productId, reviewId, request, language)
        );
    }

    @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    @DeleteMapping("/{productId}/reviews/{reviewId}")
    public ApiResponse<Boolean> delete(
            @PathVariable Long productId,
            @PathVariable Long reviewId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return ApiResponse.successResponse(productReviewService.delete(productId, reviewId, language));
    }
}
