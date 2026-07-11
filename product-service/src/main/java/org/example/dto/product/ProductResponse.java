package org.example.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.example.enums.Currency;
import org.example.enums.PriceType;
import org.example.enums.ProductModerationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
@Setter
@Getter
public class ProductResponse {
    private Long id;
    private Long companyId;
    private Long sellerId;
    private Long categoryId;
    private String name;
    private String slug;
    private String shortDescription;
    private String description;
    private PriceType priceType;
    private Double price;
    private Currency currency;

    private Map<String, Object> attributes;
    private ProductModerationStatus status;
    private Boolean isActive;
    private Boolean isPromoted;
    private LocalDateTime promotedUntil;
    private String rejectReason;
    private Long viewsCountCache;
    private Long favoritesCountCache;
    private LocalDateTime createdAt;

    private List<ProductImageResponse> images;
}
