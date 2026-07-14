package org.example.dto.internal;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class SellerProductCardResponse {
    private Long productId;
    private String name;
    private String imageUrl;
    private BigDecimal price;
    private String currency;
    private String status;
    private Long viewsCount;
    private Long favoritesCount;
    private LocalDateTime createdAt;
}
