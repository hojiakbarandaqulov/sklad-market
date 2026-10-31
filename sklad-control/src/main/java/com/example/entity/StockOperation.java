package com.example.entity;

import com.example.entity.base.BaseEntity;
import com.example.enums.StockOperationType;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;

/**
 * Ombor qoldig'i nima sababdan va qancha o'zgarganini operatsiyalar jurnalida saqlaydi.
 * Kirim, sotuv, qaytarish, hisobdan chiqarish yoki tuzatishdan oldingi va keyingi qoldiqni ko'rsatadi.
 * Bu tarixiy yozuvlarni o'zgartirish o'rniga, xatoni tuzatish uchun yangi operatsiya yaratiladi.
 */
@Getter
@Setter
@Entity
@Table(name = "stock_operations",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_operations_company_key",
                columnNames = {"company_id", "idempotency_key"}), indexes = {
        @Index(name = "idx_stock_operations_stock_created", columnList = "stock_id,created_date"),
        @Index(name = "idx_stock_operations_order", columnList = "order_id"),
        @Index(name = "idx_stock_operations_reservation", columnList = "reservation_id")
})
@Check(constraints = "quantity_before >= 0 AND quantity_after >= 0 AND quantity_after = quantity_before + quantity_change")
public class StockOperation extends BaseEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private WarehouseStock stock;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StockOperationType type;

    // Kirimda musbat, chiqimda manfiy miqdor yoziladi; masalan, +100 yoki -5.500.
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantityChange;

    @NotNull
    @PositiveOrZero
    @Digits(integer = 16, fraction = 3)
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantityBefore;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal quantityAfter;

    @Column(columnDefinition = "TEXT")
    private String reason;

    // Qo'lda kirim yoki tuzatishda buyurtma bo'lmasligi mumkin.
    @Positive
    private Long orderId;

    // Buyurtmasiz chiqimni ham aynan qaysi rezervdan kelganini kuzatish uchun.
    private Long reservationId;

    // Eski kirim/sotuv yozuvlarida null bo'lishi mumkin. Yangi adjustment har doim bu maydonlarni to'ldiradi.
    private Long companyId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    // Keyingi rezervlar o'zgarsa ham takroriy request'ga eski javobni qaytaramiz.
    @Column(name = "reserved_snapshot", precision = 19, scale = 3)
    private BigDecimal reservedSnapshot;
}
