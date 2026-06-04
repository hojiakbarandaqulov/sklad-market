package org.example.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public  class CompanySummary {
    private Long id;
    private String name;
    private String slug;

    @JsonProperty("logo_path")
    private String logoPath;
}