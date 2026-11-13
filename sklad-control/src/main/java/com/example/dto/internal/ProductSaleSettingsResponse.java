package com.example.dto.internal;

import java.math.BigDecimal;

public record ProductSaleSettingsResponse(
        Long productId,
        Boolean wholeSale,
        Boolean retail,
        String priceType,
        BigDecimal price,
        String currency,
        Long minProduct,
        Integer discountPercent,
        BigDecimal effectivePrice,
        Boolean showInMarket,
        String status
) {
}
