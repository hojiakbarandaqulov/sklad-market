package com.example.repository;

import com.example.entity.StockOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Boshlang'ich kirim ham tarixda qoladi; qoldiqni jurnalsiz yozmaymiz.
 */
public interface StockOperationRepository extends JpaRepository<StockOperation, Long> {
    @Query(
            "select o from StockOperation o where o.companyId=:companyId and o.idempotencyKey=:key")
    Optional<StockOperation> findByRequestKey(
            @Param("companyId") Long companyId,
            @Param("key") String key);

    // Kompaniya ombor orqali aniqlanadi: eski operatsiyalarda company_id bo'sh bo'lishi mumkin.
    @Query("""
            SELECT o FROM StockOperation o
            JOIN FETCH o.stock ws
            JOIN FETCH ws.warehouse w
            JOIN FETCH ws.inventoryProduct
            WHERE w.companyId = :companyId AND o.deleted = false
            ORDER BY o.createdDate DESC, o.id DESC
            """)
    List<StockOperation> findRecentByCompanyId(@Param("companyId") Long companyId, Pageable pageable);

}

