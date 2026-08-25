package org.example.ai.matching.dto;

import java.time.Instant;
import java.util.List;

public record BuyerOpportunityResult(
        List<BuyerOpportunity> opportunities,
        int evaluatedLeadCount,
        long totalLeadCount,
        boolean candidatesTruncated,
        Instant asOf,
        String source,
        String privacy,
        boolean automaticOutreachAllowed) {
}
