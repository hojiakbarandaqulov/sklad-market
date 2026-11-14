package org.example.dto.customer;

import org.example.enums.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

public record CustomerResponse(
        Long id,
        Long companyId,
        String name,
        String contractNumber,
        String phone,
        Integer paymentTermDays,
        ContractFile contractFile,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Map<Currency, BigDecimal> amountByCurrency,
        LocalDate nearestDueDate,
        Long unpaidInvoiceCount
) {
    public record ContractFile(String fileName, String contentType, Long sizeBytes, String downloadUrl) {
    }
}
