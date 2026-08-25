package org.example.ai.business.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteBusinessProductPage(
        List<RemoteBusinessProduct> items,
        Integer page,
        @JsonProperty("per_page") Integer perPage,
        @JsonProperty("total_elements") Long totalElements,
        @JsonProperty("total_pages") Integer totalPages) {
}
