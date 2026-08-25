package org.example.ai.business.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteCompanyPage(
        List<RemotePublicCompany> content,
        Integer totalPages,
        Long totalElements,
        Integer number) {

    public RemoteCompanyPage(List<RemotePublicCompany> content, Integer totalPages, Long totalElements) {
        this(content, totalPages, totalElements, null);
    }
}
