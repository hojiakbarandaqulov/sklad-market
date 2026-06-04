package org.example.service;

import org.example.dto.ApiResponse;
import org.example.dto.admin.ModerationDecisionRequest;
import org.example.dto.admin.PromotionRequest;
import org.example.dto.admin.ReasonRequest;
import org.example.dto.product.ProductListResponse;
import org.example.dto.product.ProductResponse;
import org.example.enums.AppLanguage;
import org.example.enums.ProductModerationStatus;

import java.util.List;

public interface AdminProductService {

    ProductListResponse getProducts(ProductModerationStatus status, Long companyId, String q, int page, int perPage, AppLanguage language);

    List<ProductResponse> getModerationQueue();

    void approve(Long id, AppLanguage language);

    void reject(Long id, ModerationDecisionRequest request, AppLanguage language);

    void block(Long id, ReasonRequest request, AppLanguage language);

    void promote(Long id, PromotionRequest request, AppLanguage language);

    ProductResponse getByProduct(Long id, AppLanguage language);
}
