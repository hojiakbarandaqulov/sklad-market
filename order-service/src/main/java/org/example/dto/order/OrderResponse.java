package org.example.dto.order;

import org.example.entity.Order;
import org.example.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long id, Long buyerId, Long companyId, OrderStatus status, String currency,
        BigDecimal totalAmount, String deliveryAddress, String note, Long reservationId, Instant expiresAt,
        Instant createdAt, Instant updatedAt, Instant shippedAt, Instant cancelledAt, String failureReason, List<Item> items) {
    public record Item(Long productId, String productName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineAmount) { }
}
