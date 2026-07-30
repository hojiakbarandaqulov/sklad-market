package org.example.repository;

import org.example.entity.ProductReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {

    @EntityGraph(attributePaths = "product")
    Page<ProductReview> findAllByProduct_IdAndIsActiveTrue(Long productId, Pageable pageable);

    @EntityGraph(attributePaths = "product")
    Page<ProductReview> findAllByProduct_CompanyIdAndIsActiveTrue(Long companyId, Pageable pageable);

    @EntityGraph(attributePaths = "product")
    Optional<ProductReview> findByProduct_IdAndBuyerId(Long productId, Long buyerId);

    @EntityGraph(attributePaths = "product")
    Optional<ProductReview> findByIdAndProduct_IdAndBuyerIdAndIsActiveTrue(
            Long reviewId,
            Long productId,
            Long buyerId
    );

    @Query("""
            select avg(r.rating)
            from ProductReview r
            where r.product.id = :productId
              and r.isActive = true
            """)
    Double findAverageRatingByProductId(@Param("productId") Long productId);

    @Query("""
            select avg(r.rating)
            from ProductReview r
            where r.product.companyId = :companyId
              and r.isActive = true
            """)
    Double findAverageRatingByCompanyId(@Param("companyId") Long companyId);
}
