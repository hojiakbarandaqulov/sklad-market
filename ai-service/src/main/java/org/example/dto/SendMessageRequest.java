package org.example.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Deliberately unvalidated by bean-validation: blank/oversized input is rejected inside the SSE
 * turn as a typed {@code invalid_input} error event (PLAN.md §6), not a plain 400 — the endpoint
 * always answers as an SSE stream.
 */
@Getter
@Setter
public class SendMessageRequest {
    private String content;
}
