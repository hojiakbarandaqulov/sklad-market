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
            Long inventoryProductId);}
