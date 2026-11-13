package com.example.dto.product;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class InventoryProductSaleSettingsResponse {
    private Long id;
    private Long productId;
    private Boolean wholeSale;
    private Boolean retail;
    private String priceType;
    private BigDecimal price;
    private String currency;
    private Long minProduct;
    private Integer discountPercent;
    private BigDecimal effectivePrice;
    private Boolean showInMarket;
    private String status;
}
