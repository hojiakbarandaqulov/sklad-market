package org.example.service.impl;

import org.example.dto.review.ProductReviewCreateRequest;
import org.example.dto.review.ProductReviewListResponse;
import org.example.entity.Product;
import org.example.entity.ProductReview;
import org.example.enums.AppLanguage;
import org.example.enums.ProductModerationStatus;
import org.example.exp.AppBadException;
import org.example.repository.ProductRepository;
import org.example.repository.ProductReviewRepository;
import org.example.service.ResourceBundleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductReviewServiceImplTest {

    @Mock
    private ProductReviewRepository productReviewRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ResourceBundleService messageService;

    @InjectMocks
    private ProductReviewServiceImpl productReviewService;

    @BeforeEach
    void setUp() {
        authenticate(101L);
        lenient().when(messageService.getMessage(anyString(), any(AppLanguage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSavesReviewForApprovedProduct() {
        Product product = product(10L, 20L, 202L);
        ProductReviewCreateRequest request = createRequest(5, "  Juda yaxshi  ");

        when(productRepository.findByIdAndModerationStatusAndIsActiveTrueAndDeletedAtIsNull(
                10L,
                ProductModerationStatus.APPROVED
        )).thenReturn(Optional.of(product));
        when(productReviewRepository.findByProduct_IdAndBuyerId(10L, 101L))
                .thenReturn(Optional.empty());
        when(productReviewRepository.saveAndFlush(any(ProductReview.class)))
                .thenAnswer(invocation -> {
                    ProductReview review = invocation.getArgument(0);
                    review.setId(1L);
                    review.setCreatedAt(LocalDateTime.now());
                    review.setModifiedDate(LocalDateTime.now());
                    return review;
                });

        var response = productReviewService.create(10L, request, AppLanguage.UZ);

        assertEquals(1L, response.getId());
        assertEquals(10L, response.getProductId());
        assertEquals(20L, response.getCompanyId());
        assertEquals(101L, response.getBuyerId());
        assertEquals(5, response.getRating());
        assertEquals("Juda yaxshi", response.getComment());
    }

    @Test
    void createReactivatesPreviouslyDeletedReview() {
        Product product = product(10L, 20L, 202L);
        ProductReview existing = review(1L, product, 101L, 2);
        existing.setIsActive(Boolean.FALSE);

        when(productRepository.findByIdAndModerationStatusAndIsActiveTrueAndDeletedAtIsNull(
                10L,
                ProductModerationStatus.APPROVED
        )).thenReturn(Optional.of(product));
        when(productReviewRepository.findByProduct_IdAndBuyerId(10L, 101L))
                .thenReturn(Optional.of(existing));
        when(productReviewRepository.saveAndFlush(existing)).thenReturn(existing);

        var response = productReviewService.create(10L, createRequest(4, "Yangilangan"), AppLanguage.UZ);

        assertTrue(existing.getIsActive());
        assertEquals(4, response.getRating());
        assertEquals("Yangilangan", response.getComment());
    }

    @Test
    void createRejectsReviewForOwnProduct() {
        Product product = product(10L, 20L, 101L);
        when(productRepository.findByIdAndModerationStatusAndIsActiveTrueAndDeletedAtIsNull(
                10L,
                ProductModerationStatus.APPROVED
        )).thenReturn(Optional.of(product));

        AppBadException exception = assertThrows(
                AppBadException.class,
                () -> productReviewService.create(10L, createRequest(5, null), AppLanguage.UZ)
        );

        assertEquals("review.own.product.not.allowed", exception.getMessage());
    }

    @Test
    void deleteUsesProductReviewAndCurrentBuyerTogether() {
        Product product = product(10L, 20L, 202L);
        ProductReview review = review(7L, product, 101L, 5);
        when(productReviewRepository.findByIdAndProduct_IdAndBuyerIdAndIsActiveTrue(
                7L,
                10L,
                101L
        )).thenReturn(Optional.of(review));

        assertTrue(productReviewService.delete(10L, 7L, AppLanguage.UZ));
        assertFalse(review.getIsActive());
        verify(productReviewRepository).save(review);
    }

    @Test
    void companyReviewsAggregateReviewsFromAllCompanyProducts() {
        Product firstProduct = product(10L, 20L, 202L);
        Product secondProduct = product(11L, 20L, 203L);
        List<ProductReview> reviews = List.of(
                review(1L, firstProduct, 101L, 5),
                review(2L, secondProduct, 102L, 4)
        );

        when(productReviewRepository.findAllByProduct_CompanyIdAndIsActiveTrue(
                eq(20L),
                any(PageRequest.class)
        )).thenReturn(new PageImpl<>(reviews, PageRequest.of(0, 20), 2));
        when(productReviewRepository.findAverageRatingByCompanyId(20L)).thenReturn(4.5);

        ProductReviewListResponse response = productReviewService
                .getCompanyProductReviews(20L, 1, 20, AppLanguage.UZ);

        assertEquals(2, response.getItems().size());
        assertEquals(2, response.getReviewCount());
        assertEquals("4.5", response.getAverageRating().toPlainString());
        assertEquals(List.of(10L, 11L), response.getItems().stream().map(item -> item.getProductId()).toList());
    }

    private void authenticate(Long profileId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("profileId", profileId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private Product product(Long productId, Long companyId, Long sellerId) {
        Product product = new Product();
        product.setId(productId);
        product.setCompanyId(companyId);
        product.setSellerId(sellerId);
        product.setName("Product " + productId);
        product.setModerationStatus(ProductModerationStatus.APPROVED);
        product.setIsActive(Boolean.TRUE);
        return product;
    }

    private ProductReview review(Long reviewId, Product product, Long buyerId, Integer rating) {
        ProductReview review = new ProductReview();
        review.setId(reviewId);
        review.setProduct(product);
        review.setBuyerId(buyerId);
        review.setRating(rating);
        review.setComment("Comment");
        review.setIsActive(Boolean.TRUE);
        review.setCreatedAt(LocalDateTime.now());
        review.setModifiedDate(LocalDateTime.now());
        return review;
    }

    private ProductReviewCreateRequest createRequest(Integer rating, String comment) {
        ProductReviewCreateRequest request = new ProductReviewCreateRequest();
        request.setRating(rating);
        request.setComment(comment);
        return request;
    }
}
