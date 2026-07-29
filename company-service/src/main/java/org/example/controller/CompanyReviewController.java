package org.example.controller;

import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.review.CompanyReviewListResponse;
import org.example.dto.review.ReviewCreateRequest;
import org.example.dto.review.ReviewResponse;
import org.example.dto.review.ReviewUpdateRequest;
import org.example.enums.AppLanguage;
import org.example.service.CompanyReviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/companies/{companyId}/reviews")
public class CompanyReviewController {

    private final CompanyReviewService companyReviewService;

    @PermitAll
    @GetMapping
    public ApiResponse<CompanyReviewListResponse> getReviews(
            @PathVariable Long companyId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return companyReviewService.getReviews(companyId, page, perPage, language);
    }

    @PreAuthorize("hasRole('BUYER')")
    @PostMapping
    public ApiResponse<ReviewResponse> create(
            @PathVariable Long companyId,
            @RequestBody @Valid ReviewCreateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return companyReviewService.create(companyId, request, language);
    }

    @PreAuthorize("hasRole('BUYER')")
    @PutMapping("/{reviewId}")
    public ApiResponse<ReviewResponse> update(
            @PathVariable Long companyId,
            @PathVariable Long reviewId,
            @RequestBody @Valid ReviewUpdateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return companyReviewService.update(companyId, reviewId, request, language);
    }

    @PreAuthorize("hasRole('BUYER')")
    @DeleteMapping("/{reviewId}")
    public ApiResponse<Boolean> delete(
            @PathVariable Long companyId,
            @PathVariable Long reviewId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return companyReviewService.delete(companyId, reviewId, language);
    }
}
