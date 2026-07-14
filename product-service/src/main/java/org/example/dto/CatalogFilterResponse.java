package org.example.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
public class CatalogFilterResponse {
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Map<String, List<String>> attributes;
}
