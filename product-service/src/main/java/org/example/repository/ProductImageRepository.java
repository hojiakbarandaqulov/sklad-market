package org.example.repository;

import org.example.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, String> {
    long countByProduct_Id(Long productId);

    List<ProductImage> findByProduct_IdOrderBySortOrderAscIdAsc(Long productId);

    Optional<ProductImage> findByIdAndProduct_Id(String imageId, Long productId);

    Optional<ProductImage> findFirstByProduct_IdAndIsPrimaryTrueOrderByCreatedDateDesc(Long productId);

    @Modifying
    @Query("UPDATE ProductImage p SET p.isPrimary=false WHERE p.product.id=:productId")
    void clearPrimaryByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE ProductImage p SET p.isPrimary=true WHERE p.id=:imageId AND p.product.id=:productId")
    void isPrimary(@Param("imageId") String imageId, @Param("productId") Long productId);
}
