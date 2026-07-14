package org.example.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SellerDashboardResponse {
    private SellerDashboardSummaryResponse summary;
    private SellerDashboardTrendResponse trend;
    private List<SellerDashboardProductResponse> recentProducts;
}
