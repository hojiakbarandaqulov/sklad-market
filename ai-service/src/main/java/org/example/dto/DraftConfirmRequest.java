package org.example.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Optional buyer edits applied on top of the drafted contact fields at confirm time (the
 * DraftLeadCard's "editable contacts" — PLAN.md Phase 4). Products/quantities are never editable
 * here: they were already re-verified via the public API when the draft was created (T8).
 */
@Getter
@Setter
public class DraftConfirmRequest {
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String deliveryAddress;
    private String neededDate;
    private String comment;
}
