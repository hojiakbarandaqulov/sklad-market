package com.example.dto.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderReservationRequest(@NotNull @Positive Long companyId, @NotNull @Positive Long buyerId,
        @NotNull Instant expiresAt, @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items) {
    public record Item(@NotNull @Positive Long productId,
            @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity) { }
}
