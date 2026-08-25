package org.example.ai.error;

/**
 * Single exception type flowing from guardrails and provider calls into the SSE `error` event
 * mapping — every catch site only needs to know about this one type (PLAN.md §6: typed error
 * events, never raw 500s).
 */
public class AiChatException extends RuntimeException {

    private final AiErrorCode code;

    public AiChatException(AiErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public AiChatException(AiErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public AiErrorCode code() {
        return code;
    }
}
