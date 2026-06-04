package org.example.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.example.enums.Currency;

@Getter
@Builder
public  class SimilarProductResponse {
    private Long id;
    private String name;
    private String slug;
    private Double price;
    private Currency currency;

    @JsonProperty("is_promoted")
    private Boolean isPromoted;

    @JsonProperty("primary_image")
    private String primaryImage;
}
