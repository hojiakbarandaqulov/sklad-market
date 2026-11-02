package org.example.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;

@FeignClient(name = "product-service", url = "${PRODUCT_SERVICE_URL:}")
public interface ProductClient {

    @GetMapping("/internal/products/{id}/order-summary")
    ProductSnapshot get(@PathVariable Long id);

    // Buyurtma uchun kerak bo'lgan narx, sotuvchi va mahsulot holati.
    record ProductSnapshot(
            Long id,
            Long companyId,
            Long sellerId,
            String name,
            BigDecimal price,
            String currency,
            Long minimumQuantity,
            boolean orderable
    ) {
    }
}
