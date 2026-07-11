package org.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;
import org.example.enums.Currency;
import org.example.enums.PriceType;
import org.example.enums.ProductModerationStatus;
import org.example.enums.SaleType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private Long sellerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    private String shortDescription;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriceType priceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SaleType saleType;

    private Double price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false)
    private Long regionId;

    private Long districtId;

    private Long minProduct;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> attributesJsonb = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductModerationStatus moderationStatus = ProductModerationStatus.PENDING;

    private Boolean isPromoted = Boolean.FALSE;

    private LocalDateTime promotedUntil;

    private String rejectReason;

    private Long viewsCountCache = 0L;

    private Long favoritesCountCache = 0L;

    private LocalDateTime deletedAt;

}
