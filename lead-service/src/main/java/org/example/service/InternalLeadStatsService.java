package org.example.service;

import org.example.dto.internal.dashboard.SellerLeadStatsResponse;
import org.example.dto.internal.dashboard.SellerStatsFilterRequest;

public interface InternalLeadStatsService {
    SellerLeadStatsResponse getSellerOverview(SellerStatsFilterRequest request);
}
