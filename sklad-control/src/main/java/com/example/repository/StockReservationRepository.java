package com.example.repository;

import com.example.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import com.example.enums.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StockReservationRepository extends JpaRepository<StockReservation, Long> {
    Optional<StockReservation> findByCompanyIdAndIdempotencyKey(Long companyId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockReservation r where r.id=:id and r.deleted=false")
    Optional<StockReservation> lockPresent(@Param("id") Long id);

    @Query("select r.id from StockReservation r where r.deleted=false and r.status=:status and r.expiresAt<=:now order by r.id")
    List<Long> expiredIds(@Param("status") ReservationStatus status, @Param("now") Instant now, Pageable pageable);
}
