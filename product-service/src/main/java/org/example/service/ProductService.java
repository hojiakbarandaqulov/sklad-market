package org.example.service;

import org.example.dto.ApiResponse;
import org.example.dto.product.CreateProductRequest;
import org.example.dto.product.ProductDetailResponse;
import org.example.dto.product.ProductImageResponse;
import org.example.dto.product.ProductListResponse;
import org.example.dto.product.ProductResponse;
import org.example.dto.product.UpdateProductRequest;
import org.example.entity.Product;
import org.example.entity.ProductImage;
import org.example.enums.AppLanguage;
import org.example.enums.ProductModerationStatus;
import org.example.enums.SaleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface ProductService {
    ProductResponse create(CreateProductRequest request, AppLanguage language);

    List<ProductImageResponse> uploadImages(Long productId, List<MultipartFile> files, AppLanguage language);

    void setPrimaryImage(Long productId, String imageId, AppLanguage language);

    ApiResponse<Boolean> deleteImage(Long productId, String imageId, AppLanguage language);

    ProductListResponse getMyProducts(Long companyId, ProductModerationStatus status, int page, int perPage, AppLanguage language);

    ProductDetailResponse getPublicDetail(String slug, String sessionId, AppLanguage language);

    ProductResponse update(Long id, UpdateProductRequest request, AppLanguage language);

    void publish(Long id, AppLanguage language);

    void archive(Long id, AppLanguage language);

    void delete(Long id, AppLanguage language);

    ProductListResponse getAllProducts(int page, int perPage, AppLanguage language);

    Optional<Product> findByIdAndDeletedAtIsNull(Long productId);

    Optional<ProductImage> findFirstByProduct_IdAndIsPrimaryTrueOrderByCreatedDateDesc(Long productId);

//    Page<Product> findByCompanyIdAndModerationStatusAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(Long companyId, ProductModerationStatus productModerationStatus, PageRequest createdAt);

    Long countByModerationStatusAndDeletedAtIsNull(ProductModerationStatus productModerationStatus);

    ProductResponse toResponse(Product product);

    ProductResponse getById(Long id, AppLanguage language);

    Page<Product> findByCompanyIdAndCategoryIdAndModerationStatusAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(Long companyId, Long categoryId, ProductModerationStatus productModerationStatus, PageRequest createdAt);
}
