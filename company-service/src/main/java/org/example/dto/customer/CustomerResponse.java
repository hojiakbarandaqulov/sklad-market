package org.example.dto.customer;

import java.time.LocalDateTime;

public record CustomerResponse(
        Long id,
        Long companyId,
        String name,
        String contractNumber,
        String phone,
        Integer paymentTermDays,
        ContractFile contractFile,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record ContractFile(String fileName, String contentType, Long sizeBytes, String downloadUrl) {
    }
}
