package org.example.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SellerDashboardSummaryResponse {
    private Long activeProducts;
    private Long leads;
    private Long contacts;
    private Long totalViews;
    private Long totalFavorites;
}
