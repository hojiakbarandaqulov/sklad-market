package org.example.dto.customer;

import jakarta.validation.constraints.*;

public record CustomerUpdateRequest(
        @Size(max = 255) @Pattern(regexp = "(?s).*\\S.*") String name,
        @Size(max = 100) @Pattern(regexp = "(?s).*\\S.*") String contractNumber,
        @Size(max = 30) @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,30}$") String phone,
        @Min(1) @Max(3650) Integer paymentTermDays
) {
}
