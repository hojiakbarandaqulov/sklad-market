package org.example.config.internal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "product-service")
public interface ProductClient {

   /* @GetMapping("/internal/products/company/{companyId}")
    ProductListResponse getCompanyProducts(@PathVariable("companyId") Long companyId,
                                           @RequestParam("page") int page,
                                           @RequestParam("per_page") int perPage);*/
}
