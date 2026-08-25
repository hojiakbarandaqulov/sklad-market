package org.example.ai.business.dto;

import java.util.List;

public record BusinessSearchResponse(
        String query,
        int count,
        List<BusinessSearchItem> items,
        String scoreMeaning,
        BusinessIndexFreshness indexFreshness) {
}
