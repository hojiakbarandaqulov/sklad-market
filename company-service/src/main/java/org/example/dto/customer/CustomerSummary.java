package org.example.dto.customer;

public record CustomerSummary(long customersCount, long withContractFileCount, Double averagePaymentTermDays,
                              Long unpaidInvoiceCount, boolean debtsAvailable) {
    public CustomerSummary(long customersCount, long withContractFileCount, Double averagePaymentTermDays) {
        this(customersCount, withContractFileCount, averagePaymentTermDays, null, false);
    }
}
