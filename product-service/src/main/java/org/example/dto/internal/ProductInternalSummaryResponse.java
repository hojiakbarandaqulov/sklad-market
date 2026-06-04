package org.example.dto.internal;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ProductInternalSummaryResponse {
    private Long id;
    private Long companyId;
    private Long sellerId;
    private String name;
    private String slug;
    private Double price;
    private String currency;
    private String primaryImage;
}
