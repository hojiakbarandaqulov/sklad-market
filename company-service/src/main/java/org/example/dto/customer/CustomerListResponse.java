package org.example.dto.customer;

import java.util.List;

public record CustomerListResponse(List<CustomerResponse> items, Meta meta, CustomerSummary summary) {
    public record Meta(long total, int page, int perPage, int totalPages) {
    }
}
