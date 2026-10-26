package com.example.dto.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Mavjud productId YOKI yangi product kartasi beriladi; ikkalasi birga yuborilmaydi.
 */
@Getter
@Setter
public class InventoryProductCreateRequest {
    @NotNull
    @Positive
    private Long companyId;
    @NotNull
    @Positive
    private Long warehouseId;
    @NotBlank
    @Size(max = 100)
    private String sku;
    @Positive
    private Long productId;
    @Valid
    private ProductCardRequest product;
    @NotNull
    @DecimalMin("0")
    @Digits(integer = 16, fraction = 3)
    private BigDecimal openingQuantity = BigDecimal.ZERO;
    @NotNull
    @DecimalMin("0")
    @Digits(integer = 16, fraction = 3)
    private BigDecimal minimumStock = BigDecimal.ZERO;
    @NotNull
    private Boolean showInMarket = false;
}
