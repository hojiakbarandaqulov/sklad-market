package org.example.config.internal;

import org.example.dto.CompanyProductListResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/internal/products/company/{companyId}")
    CompanyProductListResponse getCompanyProducts(@PathVariable("companyId") Long companyId,
                                                  @RequestParam("page") int page,
                                                  @RequestParam("per_page") int perPage);
}
