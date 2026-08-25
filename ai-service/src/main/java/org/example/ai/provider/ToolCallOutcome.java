package org.example.ai.provider;

import java.util.Map;

/** The result of executing a {@link ToolCallRequest}, fed back to the model as structured data. */
public record ToolCallOutcome(String callId, String name, Map<String, Object> responsePayload) {
}
