package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.product.*;
import org.example.enums.AppLanguage;
import org.example.enums.ProductModerationStatus;
import org.example.enums.SaleType;
import org.example.service.ProductSearchService;
import org.example.service.ProductService;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductService productService;
    private final ProductSearchService productSearchService;

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ProductResponse> create(@RequestBody @Valid CreateProductRequest request,
                                               @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(productService.create(request, language));
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<List<ProductImageResponse>> uploadImages(@PathVariable Long id,
                                                                @RequestParam(value = "files") List<MultipartFile> files,
                                                                @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(productService.uploadImages(id, files, language));
    }


    @PutMapping("/{id}/images/{imageId}/set-primary")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<Map<String, String>> setPrimary(@PathVariable Long id,
                                                       @PathVariable String imageId,
                                                       @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        productService.setPrimaryImage(id, imageId, language);
        return ApiResponse.successResponse(Map.of("message", "Primary image updated"));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<Boolean> deleteImage(@PathVariable Long id,
                                            @PathVariable String imageId,
                                            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return productService.deleteImage(id, imageId, language);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ProductListResponse> getMyProducts(
            @RequestParam(value = "company_id", required = false) Long companyId,
            @RequestParam(value = "status", required = false) ProductModerationStatus status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(productService.getMyProducts(companyId, status, page, perPage, language));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id,
                                                    @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(productService.getById(id,language));
    }

    @GetMapping("/{slug}")
    public ApiResponse<ProductDetailResponse> getBySlug(
            @PathVariable String slug,
            @RequestHeader(value = "X-SESSION-ID", required = false) String sessionId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(productService.getPublicDetail(slug, sessionId, language));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ProductResponse> update(@PathVariable Long id,
                                               @RequestBody @Valid UpdateProductRequest request,
                                               @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(productService.update(id, request, language));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<Map<String, String>> publish(@PathVariable Long id,
                                                    @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        productService.publish(id, language);
        return ApiResponse.successResponse(Map.of("message", "Product sent to moderation", "status", "pending"));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<Map<String, String>> archive(@PathVariable Long id,
                                                    @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        productService.archive(id, language);
        return ApiResponse.successResponse(Map.of("message", "Product archived", "status", "archived"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER','ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, String>> delete(@PathVariable Long id,
                                                   @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        productService.delete(id, language);
        return ApiResponse.successResponse(Map.of("message", "Product deleted"));
    }

    @GetMapping("/all")
    public ApiResponse<ProductListResponse> getAllProducts(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(productService.getAllProducts(page, perPage, language));
    }

    @GetMapping("/search")
    public ApiResponse<List<ProductSearchResponse>> getSearchProduct(
            @RequestParam String query,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage) {
        return ApiResponse.successResponse(productSearchService.search(query, page, perPage));
    }

}
