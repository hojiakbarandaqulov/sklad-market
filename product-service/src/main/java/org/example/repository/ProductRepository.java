package org.example.repository;

import org.example.entity.Product;
import org.example.enums.ProductModerationStatus;
import org.example.enums.SaleType;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    @Query("""
            select new org.example.dto.CategoryCountResponse(p.categoryId, count(p.id))
            from Product p
            where p.deletedAt is null
              and p.moderationStatus = :status
              and p.isActive = true
              and p.categoryId is not null
            group by p.categoryId
            """)
    List<org.example.dto.CategoryCountResponse> countVisibleProductsByCategory(@Param("status") ProductModerationStatus status);

    long countByModerationStatusAndDeletedAtIsNull(ProductModerationStatus moderationStatus);

    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    boolean existsBySlug(String slug);

    List<Product> findTop8ByCategoryIdAndIdNotAndModerationStatusAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long categoryId,
            Long productId,
            ProductModerationStatus moderationStatus
    );

    @Query("SELECT p FROM Product p " +
            "JOIN Favorite f ON f.productId = p.id " +
            "WHERE f.userId = :userId " +
            "AND f.isActive = true " +
            "AND p.isActive = true ")
    Page<Product> findActiveFavoriteProducts(@Param("userId") Long userId, Pageable pageable);

    List<Product> findTop8ByModerationStatusAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(ProductModerationStatus moderationStatus);

    List<Product> findTop8ByModerationStatusAndIsActiveTrueAndIsPromotedTrueAndDeletedAtIsNullOrderByCreatedAtDesc(ProductModerationStatus moderationStatus);


    @NotNull
    Page<Product> findAll(Specification<Product> specification, @NotNull Pageable pageable);

    Optional<Product> findByIdAndIsActiveTrue(Long productId);

    Page<Product> findBySaleType(SaleType saleType, Pageable pageable);


    Optional<Product> findBySlugAndModerationStatusAndDeletedAtIsNull(String slug, ProductModerationStatus productModerationStatus);

    Page<Product> findByModerationStatusAndDeletedAtIsNullOrderByIsPromotedDescViewsCountCacheDesc(ProductModerationStatus moderationStatus, Pageable pageable);

    Page<Product> findByCompanyIdAndModerationStatusAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long companyId,
            ProductModerationStatus moderationStatus,
            Pageable pageable
    );

    long countByCompanyIdInAndModerationStatusAndIsActiveTrueAndDeletedAtIsNull(
            List<Long> companyIds,
            ProductModerationStatus moderationStatus
    );

    @Query("""
            select coalesce(sum(p.viewsCountCache), 0)
            from Product p
            where p.companyId in :companyIds
              and p.moderationStatus = :status
              and p.isActive = true
              and p.deletedAt is null
            """)
    Long sumViewsCountByCompanyIds(@Param("companyIds") List<Long> companyIds,
                                   @Param("status") ProductModerationStatus status);

    @Query("""
            select coalesce(sum(p.favoritesCountCache), 0)
            from Product p
            where p.companyId in :companyIds
              and p.moderationStatus = :status
              and p.isActive = true
              and p.deletedAt is null
            """)
    Long sumFavoritesCountByCompanyIds(@Param("companyIds") List<Long> companyIds,
                                       @Param("status") ProductModerationStatus status);

    Page<Product> findByCompanyIdInAndModerationStatusAndIsActiveTrueAndDeletedAtIsNull(
            List<Long> companyIds,
            ProductModerationStatus moderationStatus,
            Pageable pageable
    );


    Page<Product> findByCompanyIdAndCategoryIdAndModerationStatusAndIsActiveTrueAndDeletedAtIsNullOrderByCreatedAtDesc(Long companyId, Long categoryId, ProductModerationStatus productModerationStatus, PageRequest createdAt);
}
