package org.example.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.example.enums.Currency;
import org.example.enums.PriceType;
import org.example.enums.ProductModerationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ProductDetailResponse {
    private Long id;
    private String name;
    private String slug;
    private String description;

    @JsonProperty("short_description")
    private String shortDescription;

    @JsonProperty("price_type")
    private PriceType priceType;

    private Double price;
    private Currency currency;
    private List<ProductImageResponse> images;
    private Map<String, Object> attributes;
    private ProductModerationStatus status;

    @JsonProperty("is_promoted")
    private Boolean isPromoted;

    @JsonProperty("views_count_cache")
    private Long viewsCountCache;

    @JsonProperty("favorites_count_cache")
    private Long favoritesCountCache;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    private CompanySummary company;
    private CategorySummary category;

    @JsonProperty("similar_products")
    private List<SimilarProductResponse> similarProducts;

}
