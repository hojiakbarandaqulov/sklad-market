package org.example.ai.sse;

public record UsageEventPayload(int tokensIn, int tokensOut, long budgetRemaining) {
}
