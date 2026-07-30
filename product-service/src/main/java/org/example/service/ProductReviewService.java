package org.example.service;

import org.example.dto.review.ProductReviewCreateRequest;
import org.example.dto.review.ProductReviewListResponse;
import org.example.dto.review.ProductReviewResponse;
import org.example.dto.review.ProductReviewUpdateRequest;
import org.example.enums.AppLanguage;

public interface ProductReviewService {

    ProductReviewListResponse getProductReviews(
            Long productId,
            int page,
            int perPage,
            AppLanguage language
    );

    ProductReviewListResponse getCompanyProductReviews(
            Long companyId,
            int page,
            int perPage,
            AppLanguage language
    );

    ProductReviewResponse create(
            Long productId,
            ProductReviewCreateRequest request,
            AppLanguage language
    );

    ProductReviewResponse update(
            Long productId,
            Long reviewId,
            ProductReviewUpdateRequest request,
            AppLanguage language
    );

    Boolean delete(Long productId, Long reviewId, AppLanguage language);
}
