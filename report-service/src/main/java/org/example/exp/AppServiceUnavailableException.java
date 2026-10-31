package org.example.exp;

public class AppServiceUnavailableException extends RuntimeException {
    public AppServiceUnavailableException(String message) {
        super(message);
    }
}
