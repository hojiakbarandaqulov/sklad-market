package org.example.exp;

public class AppConflictException extends LeadAcceptanceException {
    public AppConflictException(String message) {
        super(409, message);
    }
}
