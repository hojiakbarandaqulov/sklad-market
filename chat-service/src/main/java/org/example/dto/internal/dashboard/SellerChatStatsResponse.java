package org.example.dto.internal.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SellerChatStatsResponse {
    private Long totalThreads;
    private List<MonthlyCountResponse> monthlyChats;
}
