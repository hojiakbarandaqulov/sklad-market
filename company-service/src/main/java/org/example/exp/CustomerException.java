package org.example.exp;

import lombok.Getter;

@Getter
public class CustomerException extends RuntimeException {
    private final int status;

    public CustomerException(int status, String message) {
        super(message);
        this.status = status;
    }
}
