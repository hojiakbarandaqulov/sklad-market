package com.example.repository;

import com.example.entity.StockReservationItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface StockReservationItemRepository extends JpaRepository<StockReservationItem, Long> {
    @Query("select i from StockReservationItem i where i.reservation.id=:id and i.deleted=false order by i.stock.id")
    List<StockReservationItem> forReservation(@Param("id") Long id);

    // To'plamni pagination bilan fetch-join qilmaymiz: faqat sahifadagi rezervlarning tarkibi olinadi.
    @Query("""
            select i from StockReservationItem i
            join fetch i.stock s
            join fetch s.warehouse
            join fetch s.inventoryProduct
            where i.reservation.id in :ids and i.deleted=false
            order by i.reservation.id, s.id
            """)
    List<StockReservationItem> forReservations(@Param("ids") List<Long> ids);
}
