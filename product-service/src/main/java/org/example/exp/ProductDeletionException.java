package org.example.exp;

import lombok.Getter;

@Getter
public class ProductDeletionException extends RuntimeException {
    private final int status;

    public ProductDeletionException(int status, String message) {
        super(message);
        this.status = status;
    }
}
