package org.example.exp;

import lombok.Getter;
import java.util.Map;

@Getter
public class LeadAcceptanceException extends RuntimeException {
    private final int status;
    private final Map<String, String> errors;
    private final String upstreamTraceId;
    public LeadAcceptanceException(int status, String message) {
        this(status, message, Map.of(), null);
    }
    public LeadAcceptanceException(int status, String message, Map<String, String> errors, String upstreamTraceId) {
        super(message);
        this.status = status;
        this.errors = Map.copyOf(errors);
        this.upstreamTraceId = upstreamTraceId;
    }
}
