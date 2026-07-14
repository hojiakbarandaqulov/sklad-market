package org.example.config.clent;

import org.example.dto.dashboard.SellerStatsFilterRequest;
import org.example.dto.dashboard.internal.ChatSellerStatsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "chat-service")
public interface ChatClient {

    @PostMapping("/internal/chats/stats/seller/overview")
    ChatSellerStatsResponse getSellerOverview(@RequestBody SellerStatsFilterRequest request);

    @PutMapping("/internal/chats/{id}/block")
    void blockThread(@PathVariable("id") Long id);

}
