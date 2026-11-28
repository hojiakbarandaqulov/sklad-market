package org.example.exp;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CompanyMemberException extends RuntimeException {
    private final HttpStatus status;

    public CompanyMemberException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
