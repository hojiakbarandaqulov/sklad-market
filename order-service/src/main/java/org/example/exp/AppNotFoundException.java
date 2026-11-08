package org.example.exp;

public class AppNotFoundException extends OrderException {
    public AppNotFoundException(String message) {
        super(404, message);
    }
}
