package org.example.ai.intent.dto;

import java.time.Instant;
import java.util.List;

/** Bounded seller-search result with enough provenance to avoid implying an exhaustive ranking. */
public record BuyingIntentMatchResult(
        List<BuyingIntentMatchResponse> items,
        int evaluatedIntentCount,
        long totalIntentCount,
        boolean candidatesTruncated,
        Instant asOf,
        String privacy,
        boolean automaticOutreachAllowed) {
}
