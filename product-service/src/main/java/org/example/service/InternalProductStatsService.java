package org.example.service;

import org.example.dto.internal.SellerProductCardResponse;
import org.example.dto.internal.SellerProductStatsFilterRequest;
import org.example.dto.internal.SellerProductStatsResponse;

import java.util.List;

public interface InternalProductStatsService {
    SellerProductStatsResponse getSellerOverview(SellerProductStatsFilterRequest request);

    List<SellerProductCardResponse> getRecentProducts(SellerProductStatsFilterRequest request);
}
