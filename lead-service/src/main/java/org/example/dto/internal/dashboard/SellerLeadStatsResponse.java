package org.example.dto.internal.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SellerLeadStatsResponse {
    private Long totalLeads;
    private List<MonthlyCountResponse> monthlyLeads;
}
