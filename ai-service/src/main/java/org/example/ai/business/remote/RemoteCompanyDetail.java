package org.example.ai.business.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteCompanyDetail(
        Long id,
        String name,
        String slug,
        String status,
        String address,
        String phonePrimary,
        String phoneSecondary,
        String website,
        String lat,
        String lng) {
}
