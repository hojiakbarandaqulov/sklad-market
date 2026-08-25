package org.example.ai.provider;

import java.util.Map;

/**
 * A single model-requested tool invocation, vendor-neutral apart from an optional opaque
 * continuation signature. Gemini 3 requires that signature to be replayed unchanged with a
 * function call before its result is submitted; providers that do not use it leave it null.
 */
public record ToolCallRequest(
        String callId, String name, Map<String, Object> args, byte[] continuationSignature) {

    public ToolCallRequest(String callId, String name, Map<String, Object> args) {
        this(callId, name, args, null);
    }

    public ToolCallRequest {
        continuationSignature = continuationSignature == null ? null : continuationSignature.clone();
    }

    @Override
    public byte[] continuationSignature() {
        return continuationSignature == null ? null : continuationSignature.clone();
    }
}
