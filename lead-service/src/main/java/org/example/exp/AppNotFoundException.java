package org.example.exp;

public class AppNotFoundException extends LeadAcceptanceException {
    public AppNotFoundException(String message) {
        super(404, message);
    }
}
