package org.example.dto.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SellerDashboardTrendResponse {
    private List<String> labels;
    private List<Long> viewsSeries;
    private List<Long> leadsSeries;
    private List<Long> chatsSeries;
    private Long totalViews;
    private Long totalLeads;
    private Long totalChats;
}
