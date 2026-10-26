package com.example.dto.stockOperation;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record RezervRequest(
        @NotNull @Positive
        Long warehouseId,
        @NotNull @Positive
        Long inventoryProductId,
        @NotNull @Positive
        @Digits(integer = 16, fraction = 3)
        BigDecimal quantity,
        @NotBlank(message = "reason required")
        @Size(max = 2000)
        String reason,
        @NotBlank(message = "idempotencyKey required")
        @Size(max = 128)
        String idempotencyKey
) {
}
