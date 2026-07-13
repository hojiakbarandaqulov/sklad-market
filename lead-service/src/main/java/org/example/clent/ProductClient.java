package org.example.clent;

import org.example.dto.internal.ProductInternalSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "product-service"
       /* url = "${services.product-service.url}",
        configuration = FeignClientConfig.class,
        dismiss404 = true*/
)
public interface ProductClient {

    @GetMapping("/internal/products/{id}/summary")
    ProductInternalSummaryResponse getById(@PathVariable Long id);
}
