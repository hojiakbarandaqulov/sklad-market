package org.example.exp;

public class AppServiceUnavailableException extends OrderException {
    public AppServiceUnavailableException(String message) {
        super(503, message);
    }

    public AppServiceUnavailableException(String message, String diagnostic) {
        super(503, message, diagnostic);
    }
}
