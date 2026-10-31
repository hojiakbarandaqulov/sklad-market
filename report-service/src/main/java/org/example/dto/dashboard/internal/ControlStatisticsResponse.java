package org.example.dto.dashboard.internal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Ombor servisining javobi va dashboarddagi birlashtirilgan ombor statistikasi. */
public record ControlStatisticsResponse(
        Long companyId,
        Long productsCount,
        Long lowStockCount,
        Long outOfStockCount,
        Long reservedProductsCount,
        List<RecentOperationResponse> recentOperations) {

    public record RecentOperationResponse(
            Long companyId,
            Long id,
            Long warehouseId,
            Long inventoryProductId,
            String sku,
            String type,
            BigDecimal quantityBefore,
            BigDecimal quantityChange,
            BigDecimal quantityAfter,
            String reason,
            Long createdBy,
            LocalDateTime createdDate) {

        // Bir nechta kompaniya tarixi birlashtirilganda operatsiyaning egasi ham ko'rinadi.
        public RecentOperationResponse withCompanyId(Long ownerCompanyId) {
            return new RecentOperationResponse(ownerCompanyId, id, warehouseId, inventoryProductId, sku, type,
                    quantityBefore, quantityChange, quantityAfter, reason, createdBy, createdDate);
        }
    }
}
