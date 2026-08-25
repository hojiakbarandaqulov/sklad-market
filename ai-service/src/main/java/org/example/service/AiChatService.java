package org.example.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.UUID;

public interface AiChatService {

    /**
     * Ownership of {@code conversationId} must already be verified by the caller. {@code
     * bearerToken} is the caller's raw JWT, forwarded verbatim to any tool call made during this
     * turn (PLAN.md §4.2 item 1) — never stored, never logged. {@code callerRoles} is the caller's
     * FULL live role set for THIS request (PLAN.md Phase 6) — used for tool-registry gating instead
     * of the conversation's single-role creation-time snapshot.
     */
    SseEmitter streamMessage(
            String userSub, UUID conversationId, String content, String acceptLanguage, String bearerToken, Set<String> callerRoles);
}
