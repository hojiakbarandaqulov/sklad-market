package org.example.exception;

public class AiNotFoundException extends RuntimeException {
    public AiNotFoundException(String message) {
        super(message);
    }
}
