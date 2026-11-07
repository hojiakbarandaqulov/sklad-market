package org.example.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record LeadAcceptRequest(@NotBlank @Size(max=128) String idempotencyKey,
        boolean buyerAgreementConfirmed,
        @NotEmpty @Size(max=100) List<@NotNull @Valid Item> items) {
    public record Item(@NotNull @Positive Long productId,
            @NotNull @Positive @Digits(integer=16,fraction=3) BigDecimal quantity) {}
}
