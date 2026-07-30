package org.example.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.PagedResponse;
import org.example.dto.chat.*;
import org.example.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chats")
@Tag(name = "Chat API", description = "Buyer va seller o'rtasidagi chatlarni boshqarish API'lari")
public class ChatController {

    private final ChatService chatService;

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @GetMapping
    @Operation(
            summary = "Chatlar ro'yxatini olish",
            description = "Login qilgan foydalanuvchining buyer yoki seller sifatidagi chatlarini sahifalab qaytaradi."
    )
    public ApiResponse<PagedResponse<ChatThreadResponse>> getThreads(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage) {
        return ApiResponse.successResponse(chatService.getThreads(page, perPage));
    }

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @PostMapping("/create")
    @Operation(
            summary = "Chat yaratish yoki avvalgi chatni ochish",
            description = "Buyer kompaniyaga yoki uning mahsulotiga chat ochadi. Xuddi shu chat mavjud bo'lsa yangi yozuv yaratilmaydi."
    )
    public ApiResponse<ChatCreateResponse> createThread(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language,
            @RequestBody @Valid CreateChatRequest request) {
        return ApiResponse.successResponse(chatService.createThread(request));
    }

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @GetMapping("/{threadId}/messages")
    @Operation(
            summary = "Chat xabarlarini olish",
            description = "before_id berilsa o'sha ID'dan oldingi xabarlar olinadi; bu yuqoriga scroll pagination uchun ishlatiladi."
    )
    public ApiResponse<PagedResponse<ChatMessageResponse>> getMessages(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language,
            @PathVariable Long threadId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestParam(value = "before_id", required = false) Long beforeId) {
        return ApiResponse.successResponse(chatService.getMessages(threadId, page, perPage, beforeId));
    }

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @GetMapping("/unread-count")
    @Operation(
            summary = "O'qilmagan xabarlar sonini olish",
            description = "Foydalanuvchining barcha buyer va seller chatlaridagi jami o'qilmagan xabarlar sonini qaytaradi."
    )
    public ApiResponse<UnreadCountResponse> getUnreadCount(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language) {
        return ApiResponse.successResponse(chatService.getUnreadCount());
    }

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @PostMapping(value = "/{threadId}/messages/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Chat rasmini yuklash",
            description = "Rasmni MinIO'ga yuklaydi va WebSocket message eventida yuboriladigan attachment_key qaytaradi. Limit: 5 MB."
    )
    public ApiResponse<UploadAttachmentResponse> uploadImage(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language,
            @PathVariable Long threadId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.successResponse(chatService.uploadAttachment(threadId, file));
    }

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @PostMapping(value = "/{threadId}/messages/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Chat faylini yuklash",
            description = "Faylni MinIO'ga yuklaydi va attachment_key qaytaradi. Limit: 10 MB."
    )
    public ApiResponse<UploadAttachmentResponse> uploadFile(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language,
            @PathVariable Long threadId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.successResponse(chatService.uploadFileAttachment(threadId, file));
    }

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @DeleteMapping("/{threadId}")
    @Operation(
            summary = "Chatni o'z ro'yxatidan yashirish",
            description = "Chat bazadan o'chmaydi; faqat so'rov yuborgan buyer yoki seller ro'yxatida ko'rinmaydi."
    )
    public ApiResponse<Map<String, String>> hideThread(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language,
            @PathVariable Long threadId) {
        chatService.hideThread(threadId);
        return ApiResponse.successResponse(Map.of("message", "Thread hidden"));
    }

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @PostMapping("/ws-token")
    @Operation(
            summary = "WebSocket uchun qisqa muddatli token olish",
            description = "REST JWT asosida chat WebSocket ulanishida ishlatiladigan alohida ws_token qaytaradi."
    )
    public ApiResponse<WsTokenResponse> issueWsToken(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language) {
        return ApiResponse.successResponse(chatService.issueWsToken());
    }
}
