package org.example.exp;

public class AppConflictException extends OrderException {
    public AppConflictException(String message) {
        super(409, message);
    }
}
