package org.example.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.enums.Currency;
import org.example.enums.VerificationStatus;

import java.math.BigDecimal;

@Getter
@Setter
public class CatalogMapItemResponse {
    private Long productId;
    private String productName;
    private String productSlug;

    private Long companyId;
    private String companyName;
    private String companySlug;
    private String companyAddress;

    private String lat;
    private String lng;

    private Double price;
    private Currency currency;
    private VerificationStatus verifiedCompany;
}

