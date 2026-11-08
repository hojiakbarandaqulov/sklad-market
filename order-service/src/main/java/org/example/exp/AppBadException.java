package org.example.exp;

public class AppBadException extends OrderException {
    public AppBadException(String message) {
        super(400, message);
    }
    public AppBadException(String message, String diagnostic) {
        super(400, message, diagnostic);
    }
}
