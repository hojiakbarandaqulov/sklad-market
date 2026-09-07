package com.example.entity;

import com.example.entity.base.BaseEntity;
import com.example.enums.RegionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "regions",
        indexes = @Index(name = "idx_regions_deleted_sort_order", columnList = "deleted, sort_order")
)
public class Region extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name_uz", nullable = false, length = 100)
    private String nameUz;

    @Column(name = "name_ru", nullable = false, length = 100)
    private String nameRu;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegionType type;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
