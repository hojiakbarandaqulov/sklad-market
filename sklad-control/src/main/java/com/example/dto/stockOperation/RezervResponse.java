package com.example.dto.stockOperation;

import com.example.enums.ReservationStatus;
import com.example.enums.StockOperationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RezervResponse(
        Long id,
        ReservationStatus type,
        Long warehouseId,
        Long inventoryProductId,
        BigDecimal quantity,
        BigDecimal reserved,
        BigDecimal available,
        LocalDateTime createdDate
) {
}
