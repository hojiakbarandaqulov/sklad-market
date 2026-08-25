package org.example.ai.intent.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/** Explicit acknowledgement required before user-authored buying-intent text becomes seller-visible. */
public record BuyingIntentPublicationRequest(
        @NotNull
        @AssertTrue(message = "must be true to publish seller-visible text")
        Boolean publicationConsent) {
}
