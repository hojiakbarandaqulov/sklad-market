package org.example.dto.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.example.enums.Currency;
import org.example.enums.PriceType;

import java.math.BigDecimal;

public record SaleSettingsPatchRequest(
        Boolean wholeSale,
        Boolean retail,
        PriceType priceType,
        @DecimalMin("0") @Digits(integer = 16, fraction = 2) BigDecimal price,
        Currency currency,
        @Positive Long minProduct,
        @Min(0) @Max(99) Integer discountPercent,
        Boolean showInMarket
) {
}
