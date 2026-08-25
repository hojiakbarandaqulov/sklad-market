package org.example.ai.tool;

import java.util.Set;
import java.util.UUID;

/**
 * Per-turn caller context available to every {@link AgentTool} execution. {@code bearerToken} is
 * the raw JWT string (no {@code "Bearer "} prefix) forwarded verbatim to the gateway — PLAN.md
 * §4.2 item 1: the user's own JWT is the only downstream credential, never a service account.
 * {@code conversationId} lets draft-producing tools (Phase 4) persist an {@code action_draft} row
 * scoped to the turn's conversation.
 */
public record ToolExecutionContext(UUID conversationId, String userSub, String bearerToken, Set<String> roles, String acceptLanguage) {
}
