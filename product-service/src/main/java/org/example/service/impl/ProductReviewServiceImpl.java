package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.review.ProductReviewCreateRequest;
import org.example.dto.review.ProductReviewListResponse;
import org.example.dto.review.ProductReviewResponse;
import org.example.dto.review.ProductReviewUpdateRequest;
import org.example.entity.Product;
import org.example.entity.ProductReview;
import org.example.enums.AppLanguage;
import org.example.enums.ProductModerationStatus;
import org.example.exp.AppBadException;
import org.example.repository.ProductRepository;
import org.example.repository.ProductReviewRepository;
import org.example.service.ProductReviewService;
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

@Service
@RequiredArgsConstructor
public class ProductReviewServiceImpl implements ProductReviewService {

    private final ProductReviewRepository productReviewRepository;
    private final ProductRepository productRepository;
    private final ResourceBundleService messageService;

    @Override
    @Transactional(readOnly = true)
    public ProductReviewListResponse getProductReviews(
            Long productId,
            int page,
            int perPage,
            AppLanguage language
    ) {
        findReviewableProduct(productId, language);
        PageRequest pageable = reviewPageRequest(page, perPage, language);
        Page<ProductReview> reviewPage = productReviewRepository
                .findAllByProduct_IdAndIsActiveTrue(productId, pageable);
        Double averageRating = productReviewRepository.findAverageRatingByProductId(productId);
        return toListResponse(reviewPage, averageRating, page, perPage);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductReviewListResponse getCompanyProductReviews(
            Long companyId,
            int page,
            int perPage,
            AppLanguage language
    ) {
        if (companyId == null || companyId < 1) {
            throw new AppBadException(messageService.getMessage("company.not.found", language));
        }
        PageRequest pageable = reviewPageRequest(page, perPage, language);
        Page<ProductReview> reviewPage = productReviewRepository
                .findAllByProduct_CompanyIdAndIsActiveTrue(companyId, pageable);
        Double averageRating = productReviewRepository.findAverageRatingByCompanyId(companyId);
        return toListResponse(reviewPage, averageRating, page, perPage);
    }

    @Override
    @Transactional
    public ProductReviewResponse create(
            Long productId,
            ProductReviewCreateRequest request,
            AppLanguage language
    ) {
        Long buyerId = requireCurrentUserId(language);
        Product product = findReviewableProduct(productId, language);
        checkBuyerIsNotProductOwner(product, buyerId, language);

        ProductReview review = productReviewRepository.findByProduct_IdAndBuyerId(productId, buyerId)
                .map(existing -> reactivateOrReject(existing, language))
                .orElseGet(ProductReview::new);

        review.setProduct(product);
        review.setBuyerId(buyerId);
        review.setRating(request.getRating());
        review.setComment(normalizeComment(request.getComment()));
        review.setIsActive(Boolean.TRUE);

        try {
            return toResponse(productReviewRepository.saveAndFlush(review));
        } catch (DataIntegrityViolationException exception) {
            throw new AppBadException(messageService.getMessage("review.already.exists", language));
        }
    }

    @Override
    @Transactional
    public ProductReviewResponse update(
            Long productId,
            Long reviewId,
            ProductReviewUpdateRequest request,
            AppLanguage language
    ) {
        Long buyerId = requireCurrentUserId(language);
        findReviewableProduct(productId, language);

        ProductReview review = productReviewRepository
                .findByIdAndProduct_IdAndBuyerIdAndIsActiveTrue(reviewId, productId, buyerId)
                .orElseThrow(() -> new AppBadException(
                        messageService.getMessage("review.not.found", language)
                ));

        review.setRating(request.getRating());
        review.setComment(normalizeComment(request.getComment()));
        return toResponse(productReviewRepository.save(review));
    }

    @Override
    @Transactional
    public Boolean delete(Long productId, Long reviewId, AppLanguage language) {
        Long buyerId = requireCurrentUserId(language);
        ProductReview review = productReviewRepository
                .findByIdAndProduct_IdAndBuyerIdAndIsActiveTrue(reviewId, productId, buyerId)
                .orElseThrow(() -> new AppBadException(
                        messageService.getMessage("review.not.found", language)
                ));

        review.setIsActive(Boolean.FALSE);
        productReviewRepository.save(review);
        return Boolean.TRUE;
    }

    private ProductReview reactivateOrReject(ProductReview review, AppLanguage language) {
        if (Boolean.TRUE.equals(review.getIsActive())) {
            throw new AppBadException(messageService.getMessage("review.already.exists", language));
        }
        return review;
    }

    private Product findReviewableProduct(Long productId, AppLanguage language) {
        return productRepository
                .findByIdAndModerationStatusAndIsActiveTrueAndDeletedAtIsNull(
                        productId,
                        ProductModerationStatus.APPROVED
                )
                .orElseThrow(() -> new AppBadException(
                        messageService.getMessage("product.not.found", language)
                ));
    }

    private void checkBuyerIsNotProductOwner(Product product, Long buyerId, AppLanguage language) {
        if (Objects.equals(product.getSellerId(), buyerId)) {
            throw new AppBadException(messageService.getMessage("review.own.product.not.allowed", language));
        }
    }

    private Long requireCurrentUserId(AppLanguage language) {
        Long userId = SpringSecurityUtil.getProfileId();
        if (userId == null) {
            throw new AppBadException(messageService.getMessage("user.not.found", language));
        }
        return userId;
    }

    private PageRequest reviewPageRequest(int page, int perPage, AppLanguage language) {
        if (page < 1) {
            throw new AppBadException(messageService.getMessage("page.invalid", language));
        }
        if (perPage < 1 || perPage > 100) {
            throw new AppBadException(messageService.getMessage("per.page.invalid", language));
        }
        return PageRequest.of(
                page - 1,
                perPage,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
    }

    private ProductReviewListResponse toListResponse(
            Page<ProductReview> reviewPage,
            Double averageRating,
            int page,
            int perPage
    ) {
        ProductReviewListResponse response = new ProductReviewListResponse();
        response.setItems(reviewPage.getContent().stream().map(this::toResponse).toList());
        response.setPage(page);
        response.setPerPage(perPage);
        response.setTotalElements(reviewPage.getTotalElements());
        response.setTotalPages(reviewPage.getTotalPages());
        response.setReviewCount(reviewPage.getTotalElements());
        response.setAverageRating(averageRating == null
                ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(averageRating).setScale(1, RoundingMode.HALF_UP));
        return response;
    }

    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String normalized = comment.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ProductReviewResponse toResponse(ProductReview review) {
        ProductReviewResponse response = new ProductReviewResponse();
        response.setId(review.getId());
        response.setProductId(review.getProduct().getId());
        response.setProductName(review.getProduct().getName());
        response.setCompanyId(review.getProduct().getCompanyId());
        response.setBuyerId(review.getBuyerId());
        response.setRating(review.getRating());
        response.setComment(review.getComment());
        response.setCreatedAt(review.getCreatedAt());
        response.setUpdatedAt(review.getModifiedDate());
        return response;
    }
}
