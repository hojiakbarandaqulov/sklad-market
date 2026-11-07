package org.example.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name="file-service",contextId="saleFiles")
public interface SaleFileClient {
    @GetMapping("/internal/attach/{id}/sale-info")
    Info info(@PathVariable("id") String id,@RequestHeader("Authorization") String auth);
    record Info(String id, Long size, String mimeType) {}
}
