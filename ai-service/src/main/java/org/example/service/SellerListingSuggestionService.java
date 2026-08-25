package org.example.service;

import org.example.dto.SuggestListingRequest;
import org.example.dto.SuggestListingResponse;

public interface SellerListingSuggestionService {

    /**
     * Suggest-only category + attribute-value proposal for a new listing (PLAN.md Phase 6, C8).
     * Never writes anything to any platform entity. {@code bearerToken} is forwarded to every
     * downstream call (category/attribute reads, image fetch) — PLAN.md §4.2 item 1.
     */
    SuggestListingResponse suggest(String userSub, String bearerToken, String acceptLanguage, SuggestListingRequest request);
}
