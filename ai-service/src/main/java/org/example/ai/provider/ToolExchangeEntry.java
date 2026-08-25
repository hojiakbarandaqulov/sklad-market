package org.example.ai.provider;

/**
 * One turn-local entry in an in-progress manual function-calling exchange (never persisted —
 * only the final assistant text is saved to {@code message}; tool round-trips live only for the
 * duration of a single chat turn, per Phase 2's loop in {@code AiChatServiceImpl}).
 */
public sealed interface ToolExchangeEntry permits ModelToolCallEntry, ToolResultEntry {
}
