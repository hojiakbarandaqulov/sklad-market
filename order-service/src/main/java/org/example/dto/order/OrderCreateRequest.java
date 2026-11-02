package org.example.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreateRequest(
        @NotNull @Positive Long companyId,
        @NotBlank @Size(max = 128) String idempotencyKey,
        @Size(max = 2000) String deliveryAddress,
        @Size(max = 2000) String note,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items) {
    public record Item(@NotNull @Positive Long productId,
                       @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity) {
    }
}
