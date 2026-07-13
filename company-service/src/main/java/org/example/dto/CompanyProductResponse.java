package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CompanyProductResponse {
    private Long id;
    private Long companyId;
    private Long sellerId;
    private Long categoryId;
    private String name;
    private String slug;
    private String shortDescription;
    private String description;
    private String priceType;
    private BigDecimal price;
    private String currency;
/*    private Long regionId;
    private Long districtId;*/
    private Map<String, Object> attributes;
    private String status;
    private Boolean isActive;
    private Boolean isPromoted;
    private LocalDateTime promotedUntil;
    private String rejectReason;
    private Long viewsCountCache;
    private Long favoritesCountCache;
    private LocalDateTime createdAt;
    private List<CompanyProductImageResponse> images;
}
