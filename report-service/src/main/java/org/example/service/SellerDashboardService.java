package org.example.service;

import org.example.dto.dashboard.SellerDashboardResponse;

public interface SellerDashboardService {
    SellerDashboardResponse getDashboard(Long companyId, Integer months);
}
