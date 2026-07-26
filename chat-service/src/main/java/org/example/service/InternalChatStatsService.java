package org.example.service;

import org.example.dto.internal.dashboard.SellerChatStatsResponse;
import org.example.dto.internal.dashboard.SellerStatsFilterRequest;

public interface InternalChatStatsService {
    SellerChatStatsResponse getSellerOverview(SellerStatsFilterRequest request);
}
