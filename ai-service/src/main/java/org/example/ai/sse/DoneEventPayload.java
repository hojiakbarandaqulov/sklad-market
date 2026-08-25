package org.example.ai.sse;

import java.util.UUID;

public record DoneEventPayload(UUID messageId, UUID conversationId) {
}
