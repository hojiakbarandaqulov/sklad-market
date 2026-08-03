package org.example.service;

import org.example.dto.ApiResponse;
import org.example.dto.review.*;
import org.example.enums.AppLanguage;

public interface CompanyReviewService {

    ApiResponse<CompanyReviewListResponse> getReviews(
            Long companyId,
            int page,
            int perPage,
            AppLanguage language
    );

    ApiResponse<ReviewResponse> create(
            Long companyId,
            ReviewCreateRequest request,
            AppLanguage language
    );

    ApiResponse<ReviewResponse> update(
            Long companyId,
            Long reviewId,
            ReviewUpdateRequest request,
            AppLanguage language
    );

    ApiResponse<Boolean> delete(Long companyId, Long reviewId, AppLanguage language);

    CompanyRatingDto getCompanyRating(Long companyId);
}
