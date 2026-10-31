package com.example.config.client;
import com.example.dto.ApiResponse;
import com.example.dto.product.ProductCardRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Foydalanuvchi JWT'si uzatiladi; product-service egalikni yana tekshiradi. */
@FeignClient(name = "product-service", configuration = CompanyFeignConfig.class)
public interface ProductClient {

    @GetMapping("/internal/control/products/matching")
    ApiResponse<List<Long>> matching(@RequestParam("companyId") Long companyId,
            @RequestParam(value="q",required=false) String q,
            @RequestParam(value="categoryId",required=false) Long categoryId,
            @RequestHeader("Accept-Language") String language);

    @PostMapping("/internal/control/products")
    ApiResponse<JsonNode> create(@RequestBody ProductCardRequest request, @RequestParam("key") String key,
            @RequestParam("visible") boolean visible, @RequestHeader("Accept-Language") String language);

    @PutMapping("/internal/control/products/{id}")
    ApiResponse<JsonNode> update(@PathVariable("id") Long id, @RequestBody ProductCardRequest request,
            @RequestHeader("Accept-Language") String language);
    @PostMapping("/internal/control/products/{id}/visibility")
    ApiResponse<JsonNode> visibility(@PathVariable("id") Long id, @RequestParam("visible") boolean visible,
            @RequestHeader("Accept-Language") String language);
    @GetMapping("/internal/control/products")
    ApiResponse<List<JsonNode>> cards(@RequestParam("companyId") Long companyId,
            @RequestParam("ids") List<Long> ids, @RequestHeader("Accept-Language") String language);
}
