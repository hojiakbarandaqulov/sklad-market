package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.admin.ModerationDecisionRequest;
import org.example.dto.admin.PromotionRequest;
import org.example.dto.admin.ReasonRequest;
import org.example.dto.product.ProductListResponse;
import org.example.dto.product.ProductResponse;
import org.example.enums.AppLanguage;
import org.example.enums.ProductModerationStatus;
import org.example.service.AdminProductService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {

    private final AdminProductService adminProductService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ProductListResponse> getProducts(
            @RequestParam(required = false) ProductModerationStatus status,
            @RequestParam(value = "company_id", required = false) Long companyId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(adminProductService.getProducts(status, companyId, q, page, perPage, language));
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ProductResponse> getProduct(@PathVariable Long id,
                                                   @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        ProductResponse byProduct = adminProductService.getByProduct(id, language);
        return ApiResponse.successResponse(byProduct);
    }

    @GetMapping("/moderation-queue")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<ProductResponse>> getModerationQueue() {
        return ApiResponse.successResponse(adminProductService.getModerationQueue());
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ProductModerationStatus> approve(@PathVariable Long id,
                                                        @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        adminProductService.approve(id, language);
        return ApiResponse.successResponse(ProductModerationStatus.APPROVED);
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ProductModerationStatus> reject(@PathVariable Long id,
                                                       @RequestBody(required = false) ModerationDecisionRequest request,
                                                       @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        adminProductService.reject(id, request, language);
        return ApiResponse.successResponse(ProductModerationStatus.REJECTED);
    }

    @PutMapping("/{id}/block")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Boolean> block(@PathVariable Long id,
                                      @RequestBody(required = false) ReasonRequest request,
                                      @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        adminProductService.block(id, request, language);
        return ApiResponse.successResponse(true);
    }

    @PutMapping("/{id}/promote")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Boolean> promote(@PathVariable Long id,
                                        @RequestBody(required = false) PromotionRequest request,
                                        @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        adminProductService.promote(id, request, language);
        return ApiResponse.successResponse(true);
    }
}
