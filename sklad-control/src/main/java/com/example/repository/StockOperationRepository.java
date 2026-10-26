package com.example.repository;
import com.example.entity.StockOperation;
import org.springframework.data.jpa.repository.JpaRepository;
/** Boshlang'ich kirim ham tarixda qoladi; qoldiqni jurnalsiz yozmaymiz. */
public interface StockOperationRepository extends JpaRepository<StockOperation,Long> {
    @org.springframework.data.jpa.repository.Query(
            "select o from StockOperation o where o.companyId=:companyId and o.idempotencyKey=:key")
    java.util.Optional<StockOperation> findByRequestKey(
            @org.springframework.data.repository.query.Param("companyId") Long companyId,
            @org.springframework.data.repository.query.Param("key") String key);
}
