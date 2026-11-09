package org.example.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record SaleRequest(@NotBlank @Size(max = 128) String idempotencyKey,
                          @NotNull PaymentMethod paymentMethod, @Positive Long customerId,
                          @NotBlank @Size(max = 120) String buyerName, @Size(max = 32) String buyerPhone,
                          @NotNull DeliveryMethod deliveryMethod, @Size(max = 2000) String deliveryAddress,
                          @Size(max = 255) String attachmentId,
                          @NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items) {
    public enum PaymentMethod {CASH, CASHLESS, TRANSFER, CREDIT}

    public enum DeliveryMethod {PICKUP, DELIVERY}

    public record Item(@NotNull @Positive Long productId,
                       @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal quantity,
                       @NotNull @Positive @Digits(integer = 16, fraction = 3) BigDecimal unitPrice) {
    }
}
