package org.example.config.internal;

import org.example.dto.ApiResponse;
import org.example.dto.CategoryCountResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/api/v1/catalog/category-counts")
    ApiResponse<List<CategoryCountResponse>> getCategoryCounts();
}
