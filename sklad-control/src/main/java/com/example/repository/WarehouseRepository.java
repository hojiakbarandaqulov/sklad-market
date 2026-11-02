package com.example.repository;

import com.example.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long>, JpaSpecificationExecutor<Warehouse> {
    @Query("select w.id from Warehouse w where w.companyId=:companyId and w.deleted=false order by w.id")
    java.util.List<Long> companyIds(@org.springframework.data.repository.query.Param("companyId") Long companyId);
    // Omborni arxivlash va unga mahsulot kiritish bir vaqtda bajarilmaydi.
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Warehouse w where w.id=:id and w.deleted=false")
    Optional<Warehouse> lockPresent(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<Warehouse> findByIdAndDeletedFalse(Long id);
}
