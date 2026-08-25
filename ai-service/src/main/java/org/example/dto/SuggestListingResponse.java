package org.example.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Suggest-only (PLAN.md Phase 6, C8): nothing here is ever written into any platform entity — the
 * seller reviews it and re-enters values themselves in the existing "add product" flow.
 * {@code attributes} contains ONLY values that passed {@link org.example.ai.seller.CategoryAttributeSchema}
 * strict validation against the category's real schema; anything the model proposed that failed
 * validation is silently dropped from this list (never surfaced as if it were trustworthy) and
 * accounted for in {@code missingRequired} if it was a required field.
 */
@Getter
@Builder
public class SuggestListingResponse {
    private SuggestedCategoryDto category;
    private Double categoryConfidence;
    private List<SuggestedAttributeDto> attributes;
    private List<String> missingRequired;
    private String notes;
}
