package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Chat Internal API", description = "Faqat boshqa backend servislar ishlatadigan ichki endpointlar")
public class ChatInternalController {

    private final ChatService chatService;
    private final InternalChatStatsService internalChatStatsService;

    @PutMapping("/{threadId}/block")
    @Operation(summary = "Chatni bloklash", description = "Admin yoki ichki servis chatni hamma qatnashchilar uchun bloklaydi.")
    public void blockThread(@PathVariable Long threadId) {
        chatService.blockThread(threadId);
    }

    @PostMapping("/stats/seller/overview")
    @Operation(summary = "Seller chat statistikasi", description = "Dashboard uchun jami chatlar va oylik chat trendini qaytaradi.")
    public SellerChatStatsResponse sellerOverview(@RequestBody SellerStatsFilterRequest request) {
        // Dashboarddagi "contacts" va chat trendi shu endpointdan yig'iladi.
        return internalChatStatsService.getSellerOverview(request);
    }
}
