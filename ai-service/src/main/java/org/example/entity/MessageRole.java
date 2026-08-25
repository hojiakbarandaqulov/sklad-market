package org.example.entity;

import java.util.Locale;

public enum MessageRole {
    USER,
    ASSISTANT,
    TOOL;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MessageRole fromWireValue(String value) {
        return MessageRole.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
