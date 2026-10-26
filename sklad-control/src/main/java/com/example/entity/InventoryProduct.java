package com.example.entity;

import com.example.entity.base.BaseEntity;
import com.example.enums.InventoryProductStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Kompaniyaning ombor hisobiga qo'shilgan mahsulotini saqlaydi va umumiy katalog kartasi bilan bog'laydi.
 * Bir mahsulot bir nechta omborda turishi mumkin; uning miqdorlari WarehouseStock'da saqlanadi.
 * Sotuv narxi Market e'loniga tegishli bo'lgani uchun bu classda saqlanmaydi.
 */
@Getter
@Setter
@Entity
@Table(name = "inventory_products",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_products_company_sku", columnNames = {"company_id", "sku"}),
                @UniqueConstraint(name = "uk_inventory_products_company_card", columnNames = {"company_id", "catalog_card_id"})
        },
        indexes = @Index(name = "idx_inventory_products_company_status", columnList = "company_id,status"))
public class InventoryProduct extends BaseEntity {
    @NotNull
    @Positive
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    // Hozir product-service'dagi Product.id saqlanadi; boshqa baza bilan JPA bog'lanishi qo'yilmaydi.
    @NotNull
    @Positive
    @Column(name = "catalog_card_id", nullable = false)
    private Long catalogCardId;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String sku;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryProductStatus status = InventoryProductStatus.ACTIVE;
}
