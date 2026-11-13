package com.example.dto.product;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class InventoryProductSaleSettings {
    private Boolean wholeSale;
    private Boolean retail;

    // PATCH uchun null qiymatga ruxsat. Yuborilgan matn ruxsat etilgan qiymatlardan biri bo‘lishi kerak.
    @Pattern(regexp = "FIXED|FROM_PRICE|NEGOTIABLE")
    private String priceType;

    @DecimalMin("0")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal price;

    @Pattern(regexp = "UZS|USD")
    private String currency;

    @Positive
    private Long minProduct;

    @Min(0)
    @Max(99)
    private Integer discountPercent;

    private Boolean showInMarket;
}



