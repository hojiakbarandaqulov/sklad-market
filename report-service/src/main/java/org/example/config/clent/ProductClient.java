package org.example.config.clent;

import org.example.dto.dashboard.SellerDashboardProductResponse;
import org.example.dto.dashboard.SellerStatsFilterRequest;
import org.example.dto.dashboard.internal.ProductSellerStatsResponse;
import org.example.dto.internal.ProductSummaryResponse;
import org.example.dto.report.ReportBlockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "product-service")
public interface ProductClient {
    @PostMapping("/internal/products/stats/seller/overview")
    ProductSellerStatsResponse getSellerOverview(@RequestBody SellerStatsFilterRequest request);

    @PostMapping("/internal/products/stats/seller/recent-products")
    List<SellerDashboardProductResponse> getSellerRecentProducts(@RequestBody SellerStatsFilterRequest request);

    @GetMapping("/internal/products/stats/pending-count")
    Map<String, Long> getPendingCount();

    @PutMapping("/internal/products/{id}/block")
    void blockProduct(@PathVariable Long id, @RequestBody ReportBlockRequest blockRequest);

    @GetMapping("/internal/products/{id}/summary")
    ProductSummaryResponse getProductSummary(@PathVariable Long id);
}
