package org.example.dto.internal;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SellerProductStatsResponse {
    private Long activeProducts;
    private Long totalViews;
    private Long totalFavorites;
    private List<SellerMonthlyCountResponse> monthlyViews;
}
