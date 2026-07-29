package org.example.repository;

import org.example.entity.CompanyReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompanyReviewRepository extends JpaRepository<CompanyReview, Long> {

    Page<CompanyReview> findAllByCompany_IdAndDeletedFalse(Long companyId, Pageable pageable);

    boolean existsByCompany_IdAndBuyerId(Long companyId, Long buyerId);

    Optional<CompanyReview> findByIdAndCompany_IdAndBuyerIdAndDeletedFalse(
            Long reviewId,
            Long companyId,
            Long buyerId
    );

    @Query("""
            select avg(r.rating)
            from CompanyReview r
            where r.company.id = :companyId
              and r.deleted = false
            """)
    Double findAverageRatingByCompanyId(@Param("companyId") Long companyId);

    Optional<CompanyReview> findByIdAndDeletedFalse(Long reviewId);
}
