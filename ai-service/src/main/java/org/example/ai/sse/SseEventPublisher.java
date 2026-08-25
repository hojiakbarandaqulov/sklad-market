package org.example.ai.sse;

import org.example.ai.error.AiErrorCode;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Thin wrapper around {@link SseEmitter} that speaks the exact PLAN.md §6 protocol (event names +
 * JSON payload shapes) and turns a failed send (client already gone) into a checked signal instead
 * of an uncaught {@link IOException}.
 */
public class SseEventPublisher {

    private final SseEmitter emitter;

    public SseEventPublisher(SseEmitter emitter) {
        this.emitter = emitter;
    }

    public void sendToken(String text) throws IOException {
        emitter.send(SseEmitter.event().name("token").data(new TokenEventPayload(text), MediaType.APPLICATION_JSON));
    }

    public void sendToolStart(String tool, String summary) throws IOException {
        emitter.send(SseEmitter.event().name("tool_start")
                .data(new ToolStartEventPayload(tool, summary), MediaType.APPLICATION_JSON));
    }

    public void sendToolEnd(String tool, String status) throws IOException {
        emitter.send(SseEmitter.event().name("tool_end")
                .data(new ToolEndEventPayload(tool, status), MediaType.APPLICATION_JSON));
    }

    public void sendDraft(String draftId, String type, Object payload) throws IOException {
        emitter.send(SseEmitter.event().name("draft")
                .data(new DraftEventPayload(draftId, type, payload), MediaType.APPLICATION_JSON));
    }

    /** Structured, renderable search/recommendation results in addition to the model's prose. */
    public void sendResultSet(Map<String, Object> payload) throws IOException {
        emitter.send(SseEmitter.event().name("result_set")
                .data(payload, MediaType.APPLICATION_JSON));
    }

    public void sendUsage(int tokensIn, int tokensOut, long budgetRemaining) throws IOException {
        emitter.send(SseEmitter.event().name("usage")
                .data(new UsageEventPayload(tokensIn, tokensOut, budgetRemaining), MediaType.APPLICATION_JSON));
    }

    public void sendDone(UUID messageId, UUID conversationId) throws IOException {
        emitter.send(SseEmitter.event().name("done")
                .data(new DoneEventPayload(messageId, conversationId), MediaType.APPLICATION_JSON));
    }

    public void sendError(AiErrorCode code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error")
                    .data(new ErrorEventPayload(code.wireCode(), message), MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ignored) {
            // Client already disconnected; nothing left to notify.
        }
    }

    /**
     * @return {@code false} when the stream is already closed so the caller can cancel its
     * scheduled heartbeat instead of leaking a repeating task.
     */
    public boolean sendHeartbeat() {
        try {
            emitter.send(SseEmitter.event().comment("keep-alive"));
            return true;
        } catch (IOException | IllegalStateException ignored) {
            return false;
        }
    }
}
