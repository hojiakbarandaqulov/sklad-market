package org.example.ai.provider;

/** {@code json} is the raw model output text, expected (but never blindly trusted) to match the request's schema. */
public record StructuredCompletionResult(String json, TokenUsage usage) {
}
