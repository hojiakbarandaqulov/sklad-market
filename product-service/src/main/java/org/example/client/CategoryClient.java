package org.example.client;

import org.example.client.dto.CategorySummaryResponse;
import org.example.client.dto.CategoryValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "category-service")
public interface CategoryClient {

    @GetMapping("/internal/categories/{categoryId}/exists")
    CategoryValidationResponse validate(@PathVariable Long categoryId);

    @GetMapping("/internal/categories/{categoryId}/summary")
    CategorySummaryResponse getSummary(@PathVariable Long categoryId );
}
