package org.example.ai.provider;

import java.util.List;

/**
 * {@code tools} is the caller's role-filtered tool declarations for this call; empty means the
 * model cannot call anything (also how the manual loop forces a final text answer once
 * {@code AI_MAX_TOOL_ITERATIONS} is reached — see PLAN.md Phase 2). {@code pendingToolExchange} is
 * the in-progress, turn-local function-call/response history built up by earlier iterations of the
 * same turn's loop; empty on the first call of a turn.
 */
public record ChatGenerationRequest(
        String model,
        String systemInstruction,
        List<ChatMessageInput> history,
        List<ToolSpec> tools,
        List<ToolExchangeEntry> pendingToolExchange,
        float temperature,
        int maxOutputTokens) {
}
