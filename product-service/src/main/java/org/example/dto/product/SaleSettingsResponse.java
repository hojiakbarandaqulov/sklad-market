package org.example.dto.product;

import org.example.enums.Currency;
import org.example.enums.PriceType;
import org.example.enums.ProductModerationStatus;

import java.math.BigDecimal;

public record SaleSettingsResponse(
        Long productId,
        Boolean wholeSale,
        Boolean retail,
        PriceType priceType,
        BigDecimal price,
        Currency currency,
        Long minProduct,
        int discountPercent,
        BigDecimal effectivePrice,
        boolean showInMarket,
        ProductModerationStatus status
) {
}
