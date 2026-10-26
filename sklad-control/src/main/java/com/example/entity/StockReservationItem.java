package com.example.entity;

import com.example.entity.base.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;

/**
 * Rezerv tarkibidagi mahsulotning qaysi ombordan va qancha miqdorda ajratilganini saqlaydi.
 * Shu orqali bitta buyurtmani bir nechta ombordan ta'minlash va aynan o'sha qoldiqni bo'shatish mumkin.
 */
@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_stock_reservation_items_reservation_stock", columnNames = {"reservation_id", "stock_id"}),
        indexes = @Index(name = "idx_stock_reservation_items_stock", columnList = "stock_id"))
public class StockReservationItem extends BaseEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private StockReservation reservation;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private WarehouseStock stock;

    @Positive
    @Digits(integer = 16, fraction = 3)
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantity;
}
