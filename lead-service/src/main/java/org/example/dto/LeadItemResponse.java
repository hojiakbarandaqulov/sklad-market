package org.example.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class LeadItemResponse {
    private Long productId;
    private String productNameSnapshot;
    private BigDecimal priceSnapshot;
    private BigDecimal quantity;
}
