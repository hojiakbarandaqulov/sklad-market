package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.internal.dashboard.SellerChatStatsResponse;
import org.example.dto.internal.dashboard.SellerStatsFilterRequest;
import org.example.service.ChatService;
import org.example.service.InternalChatStatsService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/chats")
public class ChatInternalController {

    private final ChatService chatService;
    private final InternalChatStatsService internalChatStatsService;

    @PutMapping("/{threadId}/block")
    public void blockThread(@PathVariable Long threadId) {
        chatService.blockThread(threadId);
    }

    @PostMapping("/stats/seller/overview")
    public SellerChatStatsResponse sellerOverview(@RequestBody SellerStatsFilterRequest request) {
        // Dashboarddagi "contacts" va chat trendi shu endpointdan yig'iladi.
        return internalChatStatsService.getSellerOverview(request);
    }
}
