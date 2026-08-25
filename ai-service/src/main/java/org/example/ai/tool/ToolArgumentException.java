package org.example.ai.tool;

/** Thrown by {@link ToolArgumentValidator} when model-supplied args fail schema/enum validation. */
public class ToolArgumentException extends RuntimeException {
    public ToolArgumentException(String message) {
        super(message);
    }
}
