package org.example.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.example.dto.order.OrderCreateRequest.Item;

public record LeadAcceptRequest(@NotBlank @Size(max=128) String idempotencyKey,
        boolean buyerAgreementConfirmed,
        @NotEmpty @Size(max=100) List<@NotNull @Valid Item> items) {}
