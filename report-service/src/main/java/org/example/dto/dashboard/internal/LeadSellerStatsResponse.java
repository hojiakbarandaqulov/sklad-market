package org.example.dto.dashboard.internal;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LeadSellerStatsResponse {
    private Long totalLeads;
    private List<MonthlyCountResponse> monthlyLeads;
}
