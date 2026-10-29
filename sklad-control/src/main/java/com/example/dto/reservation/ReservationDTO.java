package com.example.dto.reservation;

import com.example.enums.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record ReservationDTO(
        Long id, Long companyId, Long orderId, String idempotencyKey,
        ReservationStatus status, String reason, Instant expiresAt,
        Instant committedAt, Instant releasedAt, LocalDateTime createdDate,
        List<Item> items
) {
    // quantity — shu rezervga ajratilgan miqdor; qolgan sonlar omborning joriy holati.
    public record Item(Long stockId, Long warehouseId, Long inventoryProductId,
                       Long productId, String sku, BigDecimal quantity,
                       BigDecimal onHand, BigDecimal reserved, BigDecimal available) { }
}
