package org.example.dto.dashboard.internal;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ChatSellerStatsResponse {
    private Long totalThreads;
    private List<MonthlyCountResponse> monthlyChats;
}
