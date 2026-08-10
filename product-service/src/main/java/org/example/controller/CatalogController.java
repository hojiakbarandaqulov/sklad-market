package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.*;
import org.example.dto.product.ProductDto;
import org.example.dto.product.ProductResponse;
import org.example.dto.product.ProductSearchResponse;
import org.example.enums.AppLanguage;
import org.example.enums.SaleType;
import org.example.service.CatalogService;
import org.example.service.ProductSearchService;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogService catalogService;
    private final ProductSearchService productSearchService;

    @GetMapping
    public ApiResponse<PagedResponse<ProductResponse>> getCatalog(@RequestParam(required = false) String q,
                                                                  @RequestParam(required = false) String category,
                                                                  @RequestParam(required = false) Long regionId,
                                                                  @RequestParam(required = false) String currency,
                                                                  @RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "20") int perPage,
                                                                  @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(catalogService.getCatalog(q, category, regionId, currency, page, perPage, language));
    }

    @GetMapping("/search")
    public ApiResponse<PageImpl<ProductSearchResponse>> search(@RequestParam(required = false) String q,
                                                               @RequestParam(required = false) String category,
                                                               @RequestParam(required = false) Long regionId,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int perPage,
                                                               @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(productSearchService.productSearch(q, category, page, perPage, language));
    }

    @GetMapping("/search/suggestions")
    public ApiResponse<SuggestionResponse> suggestions(@RequestParam String q,
                                                       @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(catalogService.suggestions(q, language));
    }

    @GetMapping("/filters")
    public ApiResponse<CatalogFilterResponse> filters(@RequestParam(required = false) String category,
                                                      @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(catalogService.filters(category, language));
    }

    @GetMapping("/category-counts")
    public ApiResponse<List<CategoryCountResponse>> getCategoryCounts(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(catalogService.categoryCounts(language));
    }

    @GetMapping("/homepage")
    public ApiResponse<CatalogHomepageResponse> homepage(@RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(catalogService.homepage(language));
    }

    @GetMapping("/saleType/product")
    public ApiResponse<PageImpl<ProductResponse>> getSaleTypeFilterProduct(
            @RequestParam(required = false) SaleType saleType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(catalogService.getSaleTypeFilterProduct(page, perPage, saleType, language));
    }

    @GetMapping("/popular")
    public ApiResponse<PageImpl<ProductDto>> getPopularFilterProduct(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int size) {
        PageImpl<ProductDto> result = catalogService.getPopularProduct(page, size);
        return ApiResponse.successResponse(result);
    }

    @GetMapping("/map")
    public ApiResponse<PagedResponse<CatalogMapItemResponse>> getCatalogMap(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(catalogService.getCatalogMap(query, category, regionId, districtId, page, perPage, language));
    }

    @GetMapping("filter/product/price")
    public ApiResponse<PageImpl<ProductResponse>> getProductFilterPrice(
            @RequestParam BigDecimal fromPrice,
            @RequestParam BigDecimal toPrice,
            Pageable pageable) {
        return ApiResponse.successResponse(catalogService.getProductFilterPrice(fromPrice, toPrice, pageable));
    }
}
