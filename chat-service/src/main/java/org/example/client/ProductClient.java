package org.example.client;

import org.example.client.dto.ProductSummaryResponse;
import org.example.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "product-service",
        url = "${services.product.url}",
        configuration = FeignClientConfig.class
)
public interface ProductClient {

    @GetMapping("/internal/products/{productId}/summary")
    ProductSummaryResponse getSummary(@PathVariable Long productId);
}
