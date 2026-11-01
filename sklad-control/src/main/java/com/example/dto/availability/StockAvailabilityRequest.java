package com.example.dto.availability;

import jakarta.validation.constraints.*;

import java.util.List;

/** productIds — Market kartalarining ID'lari; inventoryProductId emas. */
public record StockAvailabilityRequest(
        @NotNull @Positive Long companyId,
        @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> productIds
) { }
