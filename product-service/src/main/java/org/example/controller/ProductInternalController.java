package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.internal.SellerProductCardResponse;
import org.example.dto.internal.SellerProductStatsFilterRequest;
import org.example.dto.internal.SellerProductStatsResponse;
import org.example.dto.internal.ProductInternalSummaryResponse;
import org.example.dto.product.ProductListResponse;
import org.example.dto.product.ProductResponse;
import org.example.dto.admin.ReasonRequest;
import org.example.entity.Product;
import org.example.entity.ProductImage;
import org.example.enums.AppLanguage;
import org.example.enums.ProductModerationStatus;
import org.example.exp.AppBadException;
import org.example.repository.ProductImageRepository;
import org.example.repository.ProductRepository;
import org.example.service.AdminProductService;
import org.example.service.InternalProductStatsService;
import org.example.service.ProductService;
import org.example.service.impl.ProductServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/products")
public class ProductInternalController {

    private final ProductService productService;
    private final AdminProductService adminProductService;
    private final InternalProductStatsService internalProductStatsService;

    @Value("${app.media.base-url}")
    private String mediaBaseUrl;

    @GetMapping("/{productId}/summary")
    public ProductInternalSummaryResponse summary(@PathVariable Long productId) {
        Product product = productService.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new AppBadException("product.not.found"));

        ProductImage primaryImage = productService
                .findFirstByProduct_IdAndIsPrimaryTrueOrderByCreatedDateDesc(productId)
                .orElse(null);

        String primaryImageUrl = primaryImage == null ? null : mediaBaseUrl  + primaryImage.getStorageKey();

        return ProductInternalSummaryResponse.builder()
                .id(product.getId())
                .companyId(product.getCompanyId())
                .sellerId(product.getSellerId())
                .name(product.getName())
                .slug(product.getSlug())
                .price(product.getPrice())
                .currency(product.getCurrency() == null ? null : product.getCurrency().name())
                .primaryImage(primaryImageUrl)
                .build();
    }

    @GetMapping("/company/{companyId}")
    public ProductListResponse getCompanyProducts(@PathVariable Long companyId,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(value = "per_page", defaultValue = "20") int perPage) {
        Page<Product> result = productService.findByCompanyIdAndModerationStatusAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(
                companyId,
                ProductModerationStatus.APPROVED,
                PageRequest.of(Math.max(page - 1, 0), perPage, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<ProductResponse> items = result.getContent().stream()
                .map(productService::toResponse)
                .toList();

        return ProductListResponse.builder()
                .items(items)
                .page(page)
                .perPage(perPage)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @GetMapping("/stats/pending-count")
    public Map<String, Long> pendingCount() {
        return Map.of("count", productService.countByModerationStatusAndDeletedAtIsNull(ProductModerationStatus.PENDING));
    }

    @PostMapping("/stats/seller/overview")
    public SellerProductStatsResponse sellerOverview(@RequestBody SellerProductStatsFilterRequest request) {
        return internalProductStatsService.getSellerOverview(request);
    }

    @PostMapping("/stats/seller/recent-products")
    public List<SellerProductCardResponse> recentProducts(@RequestBody SellerProductStatsFilterRequest request) {
        return internalProductStatsService.getRecentProducts(request);
    }

    @PutMapping("/{productId}/block")
    public void block(@PathVariable Long productId, @RequestBody(required = false) ReasonRequest request) {
        adminProductService.block(productId, request, AppLanguage.UZ);
    }
}
