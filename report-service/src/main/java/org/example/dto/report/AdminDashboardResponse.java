package org.example.dto.report;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminDashboardResponse {
    private Long pendingProducts;
    private Long pendingCompanies;
    private Long openReports;
    private Long blockedUsers;
}
