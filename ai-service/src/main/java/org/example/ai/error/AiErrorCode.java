package org.example.ai.error;

/** Mirrors the SSE `error` event code set fixed in PLAN.md §6. */
public enum AiErrorCode {
    RATE_LIMITED("rate_limited"),
    BUDGET_EXCEEDED("budget_exceeded"),
    PROVIDER_ERROR("provider_error"),
    TIMEOUT("timeout"),
    INVALID_INPUT("invalid_input");

    private final String wireCode;

    AiErrorCode(String wireCode) {
        this.wireCode = wireCode;
    }

    public String wireCode() {
        return wireCode;
    }
}
