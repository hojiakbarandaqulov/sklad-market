package org.example.service;

import org.example.dto.internal.dashboard.SellerChatStatsResponse;
import org.example.dto.internal.dashboard.SellerStatsRequest;

public interface InternalChatStatsService {
    SellerChatStatsResponse getSellerOverview(SellerStatsRequest request);
}
