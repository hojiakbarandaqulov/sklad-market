package org.example.ai.intent.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record BuyingIntentRequest(
        @NotBlank @Size(max = 160) String category,
        @Size(max = 160) String region,
        @NotBlank @Size(max = 2000) String needText,
        @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
        @Size(max = 32) String quantityUnit,
        @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal budgetMin,
        @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal budgetMax,
        @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @NotNull @Future Instant expiresAt) {
}
