package org.example.ai.intent.service;

import org.example.exception.ActionDraftStateException;

/** Reuses the service's existing 409 mapping while keeping intent lifecycle failures explicit. */
public class BuyingIntentStateException extends ActionDraftStateException {
    public BuyingIntentStateException(String status, String message) {
        super(status, message);
    }
}
