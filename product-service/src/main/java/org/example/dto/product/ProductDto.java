package org.example.dto.product;

import lombok.Data;
import org.example.enums.Currency;
import org.example.enums.PriceType;

import java.math.BigDecimal;

@Data
public class ProductDto {
    private Long id;
    private String name;
    private String slug;
    private BigDecimal price;
    private PriceType priceType;
    private Boolean wholeSale;
    private Boolean retail;
    private Currency currency;
    private String imageUrl;       // birinchi rasm
    private Long viewsCountCache;
    private Long favoritesCountCache;
    private Boolean isPromoted;
}