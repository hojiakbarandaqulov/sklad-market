package org.example.exp;

public class AppServiceUnavailableException extends LeadAcceptanceException {
    public AppServiceUnavailableException(String message) {
        super(503, message);
    }
}
