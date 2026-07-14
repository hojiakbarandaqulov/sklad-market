package org.example.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.example.enums.Currency;
import org.example.enums.PriceType;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
public class UpdateProductRequest {

    @NotNull(message = "company_id is required")
    @JsonProperty("company_id")
    private Long companyId;

    @NotNull(message = "category_id is required")
    @JsonProperty("category_id")
    private Long categoryId;

    @NotBlank(message = "name is required")
    private String name;

    @JsonProperty("short_description")
    private String shortDescription;

    private String description;

    @NotNull(message = "price_type is required")
    @JsonProperty("price_type")
    private PriceType priceType;

    private BigDecimal price;

    @NotNull(message = "currency is required")
    private Currency currency;

    @NotNull(message = "region_id is required")
    @JsonProperty("region_id")
    private Long regionId;

    @JsonProperty("district_id")
    private Long districtId;

    private Map<String, Object> attributes;
}
