package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.review.CompanyReviewListResponse;
import org.example.dto.review.ReviewCreateRequest;
import org.example.dto.review.ReviewResponse;
import org.example.dto.review.ReviewUpdateRequest;
import org.example.entity.Company;
import org.example.entity.CompanyReview;
import org.example.enums.AppLanguage;
import org.example.exp.AppBadException;
import org.example.repository.CompanyRepository;
import org.example.repository.CompanyReviewRepository;
import org.example.service.CompanyReviewService;
import org.example.service.ResourceBundleService;
import org.example.utils.SpringSecurityUtil;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CompanyReviewServiceImpl implements CompanyReviewService {

    private final CompanyReviewRepository companyReviewRepository;
    private final CompanyRepository companyRepository;
    private final ResourceBundleService messageService;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<CompanyReviewListResponse> getReviews(
            Long companyId,
            int page,
            int perPage,
            AppLanguage language
    ) {
        findAvailableCompany(companyId, language);
        validatePagination(page, perPage, language);

        PageRequest pageable = PageRequest.of(
                page - 1,
                perPage,
                Sort.by(Sort.Order.desc("createdDate"), Sort.Order.desc("id"))
        );
        Page<CompanyReview> reviewPage = companyReviewRepository
                .findAllByCompany_IdAndDeletedFalse(companyId, pageable);

        Double average = companyReviewRepository.findAverageRatingByCompanyId(companyId);

        CompanyReviewListResponse response = new CompanyReviewListResponse();
        response.setItems(reviewPage.getContent().stream().map(this::toResponse).toList());
        response.setPage(page);
        response.setPerPage(perPage);
        response.setTotalElements(reviewPage.getTotalElements());
        response.setTotalPages(reviewPage.getTotalPages());
        response.setReviewCount(reviewPage.getTotalElements());
        response.setAverageRating(average == null
                ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP));

        return ApiResponse.successResponse(response);
    }

    @Override
    @Transactional
    public ApiResponse<ReviewResponse> create(
            Long companyId,
            ReviewCreateRequest request,
            AppLanguage language
    ) {
        Long buyerId = requireCurrentUserId(language);
        Company company = findAvailableCompany(companyId, language);
        checkBuyerIsNotCompanyOwner(company, buyerId, language);

        if (companyReviewRepository.existsByCompany_IdAndBuyerId(companyId, buyerId)) {
            throw new AppBadException(messageService.getMessage("review.already.exists", language));
        }

        CompanyReview review = new CompanyReview();
        review.setCompany(company);
        review.setBuyerId(buyerId);
        review.setRating(request.getRating());
        review.setComment(normalizeComment(request.getComment()));

        try {
            CompanyReview saved = companyReviewRepository.saveAndFlush(review);
            return ApiResponse.successResponse(
                    toResponse(saved),
                    messageService.getMessage("review.create.success", language)
            );
        } catch (DataIntegrityViolationException e) {
            throw new AppBadException(messageService.getMessage("review.already.exists", language));
        }
    }

    @Override
    @Transactional
    public ApiResponse<ReviewResponse> update(
            Long companyId,
            Long reviewId,
            ReviewUpdateRequest request,
            AppLanguage language
    ) {
        Long buyerId = requireCurrentUserId(language);
        Company company = findAvailableCompany(companyId, language);
        checkBuyerIsNotCompanyOwner(company, buyerId, language);

        CompanyReview review = companyReviewRepository
                .findByIdAndCompany_IdAndBuyerIdAndDeletedFalse(reviewId, companyId, buyerId)
                .orElseThrow(() -> new AppBadException(
                        messageService.getMessage("review.not.found", language)
                ));

        review.setRating(request.getRating());
        review.setComment(normalizeComment(request.getComment()));
        CompanyReview saved = companyReviewRepository.save(review);

        return ApiResponse.successResponse(
                toResponse(saved),
                messageService.getMessage("review.update.success", language)
        );
    }

    @Override
    public ApiResponse<Boolean> delete(Long companyId, Long reviewId, AppLanguage language) {

        Optional<CompanyReview> review = companyReviewRepository.findByIdAndDeletedFalse(reviewId);
        if (review.isEmpty()) {
            throw new AppBadException(messageService.getMessage("review.not.found", language));
        }
//        Company company = findAvailableCompany(companyId, language);
        CompanyReview companyReview = review.get();
        companyReview.setDeleted(true);
        companyReviewRepository.save(companyReview);
        return ApiResponse.successResponse(true);
    }

    private Company findAvailableCompany(Long companyId, AppLanguage language) {
        Company company = companyRepository.findByIdAndDeletedAtIsNull(companyId)
                .orElseThrow(() -> new AppBadException(
                        messageService.getMessage("company.not.found", language)
                ));

        if (Boolean.TRUE.equals(company.getIsBlocked())) {
            throw new AppBadException(messageService.getMessage("review.company.unavailable", language));
        }
        return company;
    }

    private void checkBuyerIsNotCompanyOwner(Company company, Long buyerId, AppLanguage language) {
        if (Objects.equals(company.getOwnerUserId(), buyerId)) {
            throw new AppBadException(messageService.getMessage("review.own.company.not.allowed", language));
        }
    }

    private Long requireCurrentUserId(AppLanguage language) {
        Long userId = SpringSecurityUtil.getProfileId();
        if (userId == null) {
            throw new AppBadException(messageService.getMessage("authentication.required", language));
        }
        return userId;
    }

    private void validatePagination(int page, int perPage, AppLanguage language) {
        if (page < 1) {
            throw new AppBadException(messageService.getMessage("page.invalid", language));
        }
        if (perPage < 1 || perPage > 100) {
            throw new AppBadException(messageService.getMessage("per.page.invalid", language));
        }
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String normalized = comment.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ReviewResponse toResponse(CompanyReview review) {
        ReviewResponse response = new ReviewResponse();
        response.setId(review.getId());
        response.setCompanyId(review.getCompany().getId());
        response.setBuyerId(review.getBuyerId());
        response.setRating(review.getRating());
        response.setComment(review.getComment());
        response.setCreatedAt(review.getCreatedDate());
        response.setUpdatedAt(review.getModifiedDate());
        return response;
    }
}
