package org.example.config.internal;

import org.example.dto.CompanyProductListResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/internal/products/{companyId}/company/{categoryId}")
    CompanyProductListResponse getCompanyProducts(@PathVariable("companyId") Long companyId,
                                                  @PathVariable ("categoryId") Long categoryId,
                                                  @RequestParam("page") int page,
                                                  @RequestParam("per_page") int perPage);
}
