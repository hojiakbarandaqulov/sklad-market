package com.example.dto.stockOperation;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** actualQuantity — qo'shiladigan miqdor emas, sanalgan yangi umumiy qoldiq. */
public record AdjustmentRequest(
        @NotNull @Positive Long warehouseId,
        @NotNull @Positive Long inventoryProductId,
        @NotNull @DecimalMin("0") @Digits(integer = 16, fraction = 3) BigDecimal actualQuantity,
        @NotBlank @Size(max = 2000) String reason,
        @NotBlank @Size(max = 128) String idempotencyKey
) {}
