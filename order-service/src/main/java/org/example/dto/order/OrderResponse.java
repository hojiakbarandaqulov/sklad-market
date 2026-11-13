package org.example.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        @Schema(description = "Xaridor va sotuvchi ko'radigan buyurtma raqami", example = "RQ-53")
        String orderNumber,
        Long buyerId,
        Long companyId,
        OrderStatus status,
        String currency,
        BigDecimal totalAmount,
        String deliveryAddress,
        BigDecimal longitude,
        BigDecimal latitude,
        String note,
        Long reservationId,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant shippedAt,
        Instant cancelledAt,
        String failureReason,
        List<Item> items
) {

    @Schema(name = "OrderResponseItem")
    public record Item(
            Long productId,
            String productName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineAmount
    ) {
    }
}
