package com.example.dto.stockOperation;

import com.example.enums.StockOperationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Miqdorlar jurnalning tarixiy qiymatlari; eski yozuvlarda reserved/available null bo'lishi mumkin. */
public record StockOperationDTO(
        Long id, Long companyId, Long warehouseId, String warehouseName,
        Long inventoryProductId, Long productId, String sku, StockOperationType type,
        BigDecimal quantityBefore, BigDecimal quantityChange, BigDecimal quantityAfter,
        BigDecimal reserved, BigDecimal available, String reason,
        Long orderId, Long reservationId, Long createdBy, LocalDateTime createdDate
) { }
