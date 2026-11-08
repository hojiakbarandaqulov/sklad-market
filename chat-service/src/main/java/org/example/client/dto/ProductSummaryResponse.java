package org.example.client.dto;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductSummaryResponse {
    private Long id;
    private Long companyId;
    private Long sellerId;
    private String name;
    private String slug;
    private BigDecimal price;
    private Long minimumQuantity;
    private String currency;
    private String primaryImage;
}
