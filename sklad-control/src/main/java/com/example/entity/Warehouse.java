package com.example.entity;

import com.example.entity.base.BaseEntity;
import com.example.enums.WarehouseStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Kompaniyaga tegishli ombor yoki ombor filialini saqlaydi.
 * Mahsulot miqdorlari bu classda emas, WarehouseStock orqali har bir ombor uchun alohida yuritiladi.
 */
@Getter
@Setter
@Entity
@Table(indexes = {
        @Index(name = "idx_warehouses_company_status", columnList = "company_id,status")
})
public class Warehouse extends BaseEntity {
    // Kompaniya boshqa servisda; shu sabab database foreign key qo'yilmaydi.
    @Positive
    @Column(nullable = false)
    private Long companyId;

    @Positive
    private Long companyBranchId;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Positive
    private Long regionId;

    @Column(length = 1000)
    private String address;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WarehouseStatus status = WarehouseStatus.ACTIVE;
}
