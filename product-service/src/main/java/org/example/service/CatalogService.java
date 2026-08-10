package org.example.service;

import org.example.dto.*;
import org.example.dto.product.ProductDto;
import org.example.dto.product.ProductResponse;
import org.example.enums.AppLanguage;
import org.example.enums.SaleType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface CatalogService {
    PagedResponse<ProductResponse> getCatalog(String q, String category, Long regionId, String currency, int page, int perPage, AppLanguage language);

    PagedResponse<ProductResponse> search(String q, String category, Long regionId, int page, int perPage, AppLanguage language);

    SuggestionResponse suggestions(String q, AppLanguage language);

    CatalogFilterResponse filters(String category, AppLanguage language);

    List<CategoryCountResponse> categoryCounts(AppLanguage language);

    CatalogHomepageResponse homepage(AppLanguage language);

    PageImpl<ProductResponse> getSaleTypeFilterProduct(int page, int perPage, SaleType saleType, AppLanguage language);

    PageImpl<ProductDto> getPopularProduct(int page, int size);

    PagedResponse<CatalogMapItemResponse> getCatalogMap(String query, String category, Long regionId, Long districtId, int page, int perPage, AppLanguage language);

    PageImpl<ProductResponse> getProductFilterPrice(BigDecimal fromPrice, BigDecimal toPrice, Pageable pageable);
}
