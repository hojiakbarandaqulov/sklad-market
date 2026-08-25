package org.example.exception;

import lombok.Getter;

/** Thrown when a draft confirm/cancel is attempted from a status that doesn't allow it (e.g. expired, cancelled, already confirmed elsewhere). */
@Getter
public class ActionDraftStateException extends RuntimeException {
    private final String status;

    public ActionDraftStateException(String status, String message) {
        super(message);
        this.status = status;
    }
}
