package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Deliberately unvalidated by bean-validation, mirroring {@link SendMessageRequest}: blank/oversized
 * input and image-id shape are checked inside the service and surface as a typed
 * {@code AiChatException(INVALID_INPUT, ...)} -> 400 (PLAN.md §6 error-code convention, reused here
 * for the plain-JSON endpoint via {@code GlobalExceptionHandler}).
 */
@Getter
@Setter
public class SuggestListingRequest {
    private String description;
    /** Attachment ids from the platform's own file-service upload flow — never external URLs (PLAN.md §4.2 item 2/9). */
    private List<String> imageIds;
}
