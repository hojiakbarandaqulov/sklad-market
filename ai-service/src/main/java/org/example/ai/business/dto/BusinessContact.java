package org.example.ai.business.dto;

/** Public company-service fields only. No user profile or private buyer contact is included. */
public record BusinessContact(
        String phonePrimary,
        String phoneSecondary,
        String website,
        String address) {
}
