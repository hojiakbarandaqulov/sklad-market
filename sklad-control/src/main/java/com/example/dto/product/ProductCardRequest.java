package com.example.dto.product;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Umumiy karta va savdo maydonlari product-service'ga yuboriladi. Miqdor bu DTO'da bo'lmaydi.
 */
@Getter
@Setter
public class ProductCardRequest {
    @NotNull
    @Positive
    private Long companyId;
    @NotNull
    @Positive
    private Long categoryId;
    @NotBlank
    @Size(max = 255)
    private String name;
    @Size(max = 1000)
    private String shortDescription;
    @NotBlank
    private String description;
    @NotBlank
    @Pattern(regexp = "FIXED|FROM_PRICE|NEGOTIABLE")
    private String priceType;
    @NotBlank
    @Size(max = 32)
    private String phone;
    private Boolean wholeSale = false;
    private Boolean retail = false;
    @NotNull
    @DecimalMin("0")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal price;
    @NotBlank
    @Pattern(regexp = "UZS|USD")
    private String currency;
    @NotNull
    @Positive
    private Long minProduct;
    @JsonProperty("pickup_available")
    private Boolean pickupAvailable;
    @JsonProperty("pickup_branch_id")
    @Positive
    private Long pickupBranchId;
    // O'lchov birligi kabi umumiy xususiyatlar mavjud Product.attributes ichida saqlanadi.
    private Map<String, Object> attributes;
}
