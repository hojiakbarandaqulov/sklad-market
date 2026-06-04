package org.example.config.clent;

import org.example.dto.internal.ProductSummaryResponse;
import org.example.dto.report.ReportBlockRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service")
public interface ProductClient {
    @GetMapping("/internal/products/stats/pending-count")
    Long getPendingCount();

    @PutMapping("/internal/products/{id}/block")
    void blockProduct(@PathVariable Long id, @RequestBody ReportBlockRequest blockRequest);

    @GetMapping("/internal/products/{id}/summary")
    ProductSummaryResponse getSummary(@PathVariable Long id);
}
