package org.example.service;

import org.example.document.ProductDocument;
import org.example.dto.PagedResponse;
import org.example.dto.product.ProductResponse;
import org.example.dto.product.ProductSearchResponse;
import org.example.enums.AppLanguage;
import org.springframework.data.domain.PageImpl;

import java.util.List;

public interface ProductSearchService {
    void index(ProductDocument document);

    void delete(Long productId);

    void update(ProductDocument document);
    List<ProductSearchResponse> search(String query, int page, int perPage);

    PageImpl<ProductSearchResponse> productSearch(String q, String categoryId, int page, int perPage, AppLanguage language);
}
