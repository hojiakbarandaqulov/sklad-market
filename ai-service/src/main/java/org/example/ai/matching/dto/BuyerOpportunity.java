package org.example.ai.matching.dto;

import java.util.List;

/** Seller-safe lead projection: no buyer ID, contact fields, comment, or delivery address. */
public record BuyerOpportunity(
        Long leadId,
        String status,
        String neededDate,
        int matchScore,
        List<String> reasons,
        List<RequestedItem> requestedItems,
        String nextAction,
        boolean automaticOutreachAllowed) {

    public record RequestedItem(Long productId, String productName, Integer quantity) {
    }
}
