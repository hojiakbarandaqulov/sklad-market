package org.example.ai.sse;

/** PLAN.md §6: {@code draft} SSE event — {@code {"draftId": "...", "type": "LEAD", "payload": {...}}}. */
public record DraftEventPayload(String draftId, String type, Object payload) {
}
