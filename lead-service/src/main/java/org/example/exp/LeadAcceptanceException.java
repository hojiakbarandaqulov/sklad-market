package org.example.exp;

import lombok.Getter;

@Getter
public class LeadAcceptanceException extends RuntimeException {
    private final int status;
    public LeadAcceptanceException(int status, String message) { super(message); this.status=status; }
}
