package org.example.enums;

public enum SupportParticipantRole {
    BUYER,
    SELLER,
    ADMIN,
    SUPER_ADMIN;

    public boolean isAdmin() {
        return this == ADMIN || this == SUPER_ADMIN;
    }
}
