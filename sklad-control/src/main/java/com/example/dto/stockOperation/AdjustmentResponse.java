package com.example.dto.stockOperation;

import com.example.enums.StockOperationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Miqdorlar operatsiya bajarilgan paytdagi qiymatlardir; takroriy so'rovda ham o'zgarmaydi.
 */
public record AdjustmentResponse(
        Long id, StockOperationType type, Long warehouseId, Long inventoryProductId,
        BigDecimal quantityBefore, BigDecimal quantityChange, BigDecimal quantityAfter,
        BigDecimal reserved, BigDecimal available, String reason,
        Long createdBy, LocalDateTime createdDate
) {
}
