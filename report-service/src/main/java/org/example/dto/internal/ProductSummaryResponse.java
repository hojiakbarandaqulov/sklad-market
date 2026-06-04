package org.example.dto.internal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductSummaryResponse {
    private Long id;
    private Long companyId;
    private Long sellerId;
    private String name;
    private String slug;
    private Double price;
    private String currency;
    private String primaryImage;
}