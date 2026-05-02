package org.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class CompanyProductImageResponse {
    private String id;
    private String url;
    @JsonProperty("thumbnail_urls")
    private Map<String, String> thumbnailUrls;
    @JsonProperty("is_primary")
    private Boolean isPrimary;
}
