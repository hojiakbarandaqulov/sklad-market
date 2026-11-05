package com.example.exp;

public class AppServiceUnavailableException extends RuntimeException {
    private final String diagnostic;

    public AppServiceUnavailableException(String message) {
        this(message, "upstream unavailable");
    }

    public AppServiceUnavailableException(String message, String diagnostic) {
        super(message);
        this.diagnostic = diagnostic;
    }

    public String getDiagnostic() {
        return diagnostic;
    }
}
