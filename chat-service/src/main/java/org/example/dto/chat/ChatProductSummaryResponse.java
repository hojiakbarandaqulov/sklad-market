package org.example.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ChatProductSummaryResponse {
    private Long id;
    private String name;
    private String slug;
    private BigDecimal price;
    private String currency;

    @JsonProperty("primary_image")
    private String primaryImage;
}
