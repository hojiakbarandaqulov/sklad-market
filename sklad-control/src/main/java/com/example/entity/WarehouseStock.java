package com.example.entity;

import com.example.entity.base.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;

/**
 * Bitta mahsulotning aynan bitta ombordagi umumiy miqdori, rezervi va minimal qoldig'ini saqlaydi.
 * Sotishga mavjud miqdor umumiy qoldiqdan rezervni ayirish orqali hisoblanadi.
 * Ombor va mahsulot bir kompaniyaga tegishli ekanini kelajakdagi service tekshiradi.
 */
@Getter
@Setter
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_warehouse_stocks_warehouse_product", columnNames = {"warehouse_id", "inventory_product_id"}),
        indexes = @Index(name = "idx_warehouse_stocks_inventory_product", columnList = "inventory_product_id"))
@Check(constraints = "on_hand >= 0 AND reserved >= 0 AND reserved <= on_hand AND minimum_stock >= 0")
public class WarehouseStock extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Warehouse warehouse;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private InventoryProduct inventoryProduct;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal onHand = BigDecimal.ZERO;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal reserved = BigDecimal.ZERO;

    @NotNull
    @PositiveOrZero
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal minimumStock = BigDecimal.ZERO;

    // Bir vaqtda kelgan so'rovlar bir-birining qoldiq o'zgarishini yo'qotib yubormasligi uchun.
    @Version
    @Column(nullable = false)
    private Long version;

    @Transient
    public BigDecimal getAvailable() {
        return onHand.subtract(reserved);
    }
}
