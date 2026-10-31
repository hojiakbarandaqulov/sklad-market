package org.example.dto.dashboard;

import lombok.Getter;
import lombok.Setter;
import org.example.dto.dashboard.internal.ControlStatisticsResponse;

import java.util.List;

@Getter
@Setter
public class SellerDashboardResponse {
    private SellerDashboardSummaryResponse summary;
    private SellerDashboardTrendResponse trend;
    private List<SellerDashboardProductResponse> recentProducts;
    private ControlStatisticsResponse warehouse;
}
