package org.example.repository;

import org.example.entity.Company;
import org.example.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long>, JpaSpecificationExecutor<Company> {

    Long countByOwnerUserIdAndDeletedAtIsNull(Long userId);

    Optional<Company> findByOwnerUserIdAndDeletedFalse(Long userId);

    List<Company> findAllByIdInAndDeletedAtIsNullAndIsBlockedFalseAndLatNotNullAndLngNotNull(List<Long> id);

    Company findBySlugAndDeletedAtIsNullAndVerificationStatusIn(String slug, List<VerificationStatus> verified);

    Optional<Company> findByIdAndDeletedFalse(Long id);

    Optional<Company> findByIdAndOwnerUserIdAndDeletedAtIsNull(Long id, Long ownerId);

    Optional<Company> findByIdAndDeletedAtIsNull(Long id);

    Company findAllByOwnerUserIdAndDeletedAtIsNull(Long ownerUserId);

    long countByVerificationStatusAndDeletedAtIsNull(VerificationStatus verificationStatus);

    @Query("SELECT c FROM Company c " +
            "JOIN Favorite f ON f.companyId = c.id " +
            "WHERE f.userId = :userId " +
            "AND f.deleted = false " +
            "AND c.deleted = false ")
    Page<Company> findFavoriteCompanyDeletedFalse(@Param("userId") Long userId, Pageable pageable);

    Optional<Company> findBySlug(String s);
}
