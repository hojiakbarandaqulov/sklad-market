package org.example.clent;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.dto.ApiResponse;
import org.example.dto.LeadAcceptRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@FeignClient(name="order-service")
public interface OrderAcceptClient {
    @PostMapping("/internal/orders/lead-statuses")
    ApiResponse<Map<Long, String>> leadOrderStatuses(@RequestBody List<Long> orderIds,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Accept-Language") String language);

    @PostMapping("/internal/orders/leads/{id}/accept")
    ApiResponse<JsonNode> accept(@PathVariable("id") Long id, @RequestBody LeadAcceptRequest request,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Accept-Language") String language);
}
