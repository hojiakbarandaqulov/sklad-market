package org.example.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductSummaryResponse {
    private Long id;
    private Long companyId;
    private Long sellerId;
    private String name;
    private String slug;
    private BigDecimal price;
    private String currency;
    private String primaryImage;
}
