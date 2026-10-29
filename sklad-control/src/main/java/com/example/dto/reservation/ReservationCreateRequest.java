package com.example.dto.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ReservationCreateRequest(
        @NotNull @Positive Long companyId,
        @NotBlank @Size(max = 128) String idempotencyKey,
        @NotNull Instant expiresAt,
        @Size(max = 2000) String reason,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items
) {
    public record Item(
            @NotNull @Positive Long warehouseId,
            @NotNull @Positive Long inventoryProductId,
            @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity
    ) { }
}
