package com.example.dto.stockOperation;

import com.example.enums.StockOperationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Miqdorlar hisobdan chiqarish bajarilgan paytdagi jurnal yozuvidan olinadi. */
public record WriteOffResponse(
        Long id,
        StockOperationType type,
        Long warehouseId,
        Long inventoryProductId,
        BigDecimal quantityBefore,
        BigDecimal quantityChange,
        BigDecimal quantityAfter,
        BigDecimal reserved,
        BigDecimal available,
        String reason,
        Long createdBy,
        LocalDateTime createdDate
) {
}
