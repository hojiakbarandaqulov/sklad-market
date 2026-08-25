package org.example.entity;

/** Wire/lifecycle values for {@link ActionDraft#getStatus()} (PLAN.md Phase 4 schema). */
public final class DraftStatus {
    public static final String DRAFT = "DRAFT";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";

    private DraftStatus() {
    }
}
