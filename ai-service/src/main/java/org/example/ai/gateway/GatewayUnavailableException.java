package org.example.ai.gateway;

/** 5xx, timeout, or network failure talking to the gateway. */
public class GatewayUnavailableException extends RuntimeException {
    public GatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
