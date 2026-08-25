package org.example.ai.provider;

public record TokenUsage(int promptTokens, int candidatesTokens, int totalTokens) {
}
