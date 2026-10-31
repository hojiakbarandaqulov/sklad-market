package com.example.repository;

import com.example.entity.WarehouseStock;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

public interface WarehouseStockRepository extends JpaRepository<WarehouseStock, Long> {
    @Query("select s.id from WarehouseStock s where s.warehouse.id=:warehouseId and s.inventoryProduct.id=:productId and s.deleted=false")
    Optional<Long> positionId(@Param("warehouseId") Long warehouseId, @Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WarehouseStock s where s.id=:id and s.deleted=false")
    Optional<WarehouseStock> lockPresent(@Param("id") Long id);

    @Query("select s from WarehouseStock s join fetch s.warehouse where s.inventoryProduct.id in :ids and s.deleted=false order by s.id")
    List<WarehouseStock> forProducts(@Param("ids") List<Long> ids);

    @Query("select count(s)>0 from WarehouseStock s where s.inventoryProduct.id=:id and s.deleted=false and (s.onHand>0 or s.reserved>0)")
    boolean hasBalance(@Param("id") Long id);

    @Query("""
            SELECT COUNT (s)>0 FROM WarehouseStock s
                        WHERE s.warehouse.id = :warehouseId
                                    AND s.deleted=false
                                                AND s.reserved>0
            """)
    boolean hasReservedStock(@Param("warehouseId") Long warehouseId);


    @Query("""
            SELECT COUNT (s)>0 FROM WarehouseStock s
                        WHERE s.warehouse.id = :warehouseId
                                    AND s.deleted = false
                                                AND s.onHand > 0
            """)
    boolean hasOnHandStock(@Param("warehouseId") Long warehouseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WarehouseStock>
    findByWarehouse_IdAndInventoryProduct_IdAndDeletedFalse(
            Long warehouseId,
            Long inventoryProductId);

    @Query("""
            SELECT ws FROM WarehouseStock ws
            WHERE ws.warehouse.companyId=:companyId and ws.onHand - ws.reserved <= ws.minimumStock and
            ws.deleted=false
            """)
    List<WarehouseStock> findLowStockProducts(@Param("companyId") Long companyId);

    // Bitta mahsulot bir nechta omborda tursa, qoldiqlari yig'iladi va bir marta sanaladi.
    @Query("""
            SELECT p.id AS productId,
                   SUM(ws.onHand - ws.reserved) AS available,
                   SUM(ws.minimumStock) AS minimumStock,
                   SUM(ws.reserved) AS reserved
            FROM WarehouseStock ws
            JOIN ws.warehouse w
            JOIN ws.inventoryProduct p
            WHERE w.companyId = :companyId AND p.companyId = :companyId
              AND ws.deleted = false AND w.deleted = false AND p.deleted = false
              AND w.status = com.example.enums.WarehouseStatus.ACTIVE
              AND p.status = com.example.enums.InventoryProductStatus.ACTIVE
            GROUP BY p.id
            """)
    List<CompanyStockBalance> getCompanyStockBalances(@Param("companyId") Long companyId);

    interface CompanyStockBalance {
        Long getProductId();
        BigDecimal getAvailable();
        BigDecimal getMinimumStock();
        BigDecimal getReserved();
    }

}
