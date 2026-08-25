package org.example.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Owner-scoped, authoritative state used to restore a pending AI action after chat reload. */
public record DraftDetailsResponse(
        UUID draftId,
        String type,
        String status,
        Map<String, Object> payload,
        Long leadId,
        Instant expiresAt) {
}
