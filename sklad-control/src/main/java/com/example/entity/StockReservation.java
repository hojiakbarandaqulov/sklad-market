package com.example.entity;

import com.example.entity.base.BaseEntity;
import com.example.enums.ReservationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Buyurtmaning bitta kompaniyaga tegishli mahsulotlari uchun umumiy rezerv yozuvini saqlaydi.
 * Rezerv qaysi holatda ekani, muddati va yakunlangan vaqtlarini yuritadi.
 * Aniq mahsulot, ombor va ajratilgan miqdorlar StockReservationItem'da saqlanadi.
 */
@Getter
@Setter
@Entity
@Table(name = "stock_reservations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_reservations_company_order", columnNames = {"company_id", "order_id"}),
        @UniqueConstraint(name = "uk_stock_reservations_company_key", columnNames = {"company_id", "idempotency_key"})
}, indexes = {
        @Index(name = "idx_stock_reservations_company_status", columnList = "company_id,status"),
        @Index(name = "idx_stock_reservations_status_expiry", columnList = "status,expires_at")
})
public class StockReservation extends BaseEntity {
    @Positive
    @Column(nullable = false)
    private Long companyId;

    private Long orderId;

    @Column(length = 2000)
    private String reason;

    // Bir xil so'rov qayta kelganda ikkinchi rezerv yaratilmasligi uchun kelgan kalit saqlanadi.
    @NotBlank
    @Size(max = 128)
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @NotNull
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "committed_at")
    private Instant committedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    // Jo'natish va bekor qilish so'rovlari bir vaqtda bitta rezervni ikki xil yakunlamasligi uchun.
    @Version
    @Column(nullable = false)
    private Long version;
}
