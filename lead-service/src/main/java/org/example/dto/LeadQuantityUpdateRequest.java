package org.example.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record LeadQuantityUpdateRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items) {

    public record Item(@NotNull
                       @Positive
                       Long productId,
                       @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity) {
    }
}
