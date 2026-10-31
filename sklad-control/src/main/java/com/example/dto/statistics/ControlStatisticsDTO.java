package com.example.dto.statistics;

import com.example.enums.StockOperationType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Kompaniyaning ombor statistikasi; entity va uning lazy bog'lanishlari tashqariga chiqarilmaydi. */
public record ControlStatisticsDTO(
        Long companyId,
        long productsCount,
        long lowStockCount,
        long outOfStockCount,
        long reservedProductsCount,
        List<RecentOperationDTO> recentOperations) {

    public record RecentOperationDTO(
            Long id,
            Long warehouseId,
            Long inventoryProductId,
            String sku,
            StockOperationType type,
            BigDecimal quantityBefore,
            BigDecimal quantityChange,
            BigDecimal quantityAfter,
            String reason,
            Long createdBy,
            LocalDateTime createdDate) {
    }
}
