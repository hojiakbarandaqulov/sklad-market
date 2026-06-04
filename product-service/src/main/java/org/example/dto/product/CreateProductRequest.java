package org.example.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.example.enums.Currency;
import org.example.enums.PriceType;
import org.example.enums.SaleType;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
public class CreateProductRequest {

    @NotNull(message = "company_id is required")
    private Long companyId;

    @NotNull(message = "category_id is required")
    private Long categoryId;

    @NotBlank(message = "name is required")
    private String name;

    private String shortDescription;

    @NotNull(message = "description is required")
    private String description;

    @NotNull(message = "price_type is required")
    private PriceType priceType;

    @NotNull(message = "saleType is required")
    private SaleType saleType;

    @NotNull(message = "price is required")
    private Double price;

    @NotNull(message = "currency is required")
    private Currency currency;

    @NotNull(message = "region_id is required")
    private Long regionId;

    private Long districtId;

    @NotNull(message = "minProduct")
    private Long minProduct;

    private Map<String, Object> attributes;
}
