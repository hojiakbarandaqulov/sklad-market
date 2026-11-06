package org.example.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "sklad-control")
public interface ControlInventoryClient {
    @GetMapping("/internal/control/product-links/{productId}")
    Boolean linked(@PathVariable("productId") Long productId,
            @RequestParam("companyId") Long companyId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Accept-Language") String language);
}
