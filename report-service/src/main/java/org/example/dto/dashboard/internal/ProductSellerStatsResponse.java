package org.example.dto.dashboard.internal;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProductSellerStatsResponse {
    private Long activeProducts;
    private Long totalViews;
    private Long totalFavorites;
    private List<MonthlyCountResponse> monthlyViews;
}
