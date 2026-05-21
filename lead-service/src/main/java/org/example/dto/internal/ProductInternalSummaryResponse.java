package org.example.dto.internal;

import lombok.Getter;
import lombok.Setter;
import org.example.enums.Currency;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductInternalSummaryResponse {
    private Long id;
    private Long companyId;
    private Long sellerId;
    private String name;
    private String slug;
    private BigDecimal price;
    private Currency currency;
    private String primaryImage;
}
