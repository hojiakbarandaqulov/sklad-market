package org.example.ai.provider;

/** One turn of conversation history handed to the provider. {@code role} is "user" or "model". */
public record ChatMessageInput(String role, String text) {
}
