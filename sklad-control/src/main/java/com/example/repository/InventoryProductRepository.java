package com.example.repository;

import com.example.entity.InventoryProduct;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface InventoryProductRepository extends JpaRepository<InventoryProduct, Long>, JpaSpecificationExecutor<InventoryProduct> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from InventoryProduct p where p.id=:id and p.deleted=false")
    Optional<InventoryProduct> lockPresent(@Param("id") Long id);

    @Query("select p from InventoryProduct p where p.id=:id and p.deleted=false")
    Optional<InventoryProduct> findPresent(@Param("id") Long id);

    @Query("select p from InventoryProduct p where p.companyId=:companyId and p.sku=:sku and p.deleted=false")
    Optional<InventoryProduct> findBySku(@Param("companyId") Long companyId, @Param("sku") String sku);

    @Query("select p from InventoryProduct p where p.companyId=:companyId and p.catalogCardId=:productId and p.deleted=false")
    List<InventoryProduct> findByCard(@Param("companyId") Long companyId, @Param("productId") Long productId);
}
