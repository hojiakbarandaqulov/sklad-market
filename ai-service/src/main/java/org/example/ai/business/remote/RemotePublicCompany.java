package org.example.ai.business.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RemotePublicCompany(
        Long id,
        String name,
        String slug,
        String logoUrl,
        String verificationStatus,
        Boolean isBlocked) {

    public boolean indexable() {
        return id != null && slug != null && name != null && !Boolean.TRUE.equals(isBlocked)
                && ("VERIFIED".equals(verificationStatus) || "PENDING_VERIFICATION".equals(verificationStatus));
    }
}
