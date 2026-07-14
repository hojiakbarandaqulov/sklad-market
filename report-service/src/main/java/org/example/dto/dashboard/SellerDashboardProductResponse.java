package org.example.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SellerDashboardProductResponse {
    private Long productId;
    private String name;
    private String imageUrl;
    private Double price;
    private String currency;
    private String status;
    private Long viewsCount;
    private Long favoritesCount;
    private LocalDateTime createdAt;
}
