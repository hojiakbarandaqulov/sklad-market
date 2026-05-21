package org.example.entity;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;
import org.example.enums.Currency;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
public class CartItem extends BaseEntity {
    private Long buyerId;
    private Long productId;
    private Long sellerId;
    private Long companyId;
    private String productNameSnapshot;
    private String productSlugSnapshot;
    private String primaryImageSnapshot;
    private BigDecimal priceSnapshot;
    private Currency currencySnapshot;
    private String companyNameSnapshot;
    private String companySlugSnapshot;
    private String companyLogoPathSnapshot;
    private Integer quantity;
}
