package org.example.ai.provider;

import java.util.List;

/**
 * {@code usage} is only populated on the chunk(s) that carry cumulative usage metadata (typically
 * the last one). {@code toolCalls} is non-empty only on the chunk(s) that carry a function call
 * from the model; empty for plain text chunks.
 */
public record ChatStreamChunk(String textDelta, TokenUsage usage, List<ToolCallRequest> toolCalls) {
}
