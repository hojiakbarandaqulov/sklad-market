package org.example.dto.customer;

public record CustomerSummary(long customersCount, long withContractFileCount, Double averagePaymentTermDays) {
}
