package org.example.controller;

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
public class ChatController {

    private final ChatService chatService;

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @GetMapping
    public ApiResponse<PagedResponse<ChatThreadResponse>> getThreads(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage) {
        return ApiResponse.successResponse(chatService.getThreads(page, perPage));
    }

    @PreAuthorize("hasRole('BUYER')")
    @PostMapping("/create")
    public ApiResponse<ChatCreateResponse> createThread(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language,
            @RequestBody @Valid CreateChatRequest request) {
        return ApiResponse.successResponse(chatService.createThread(request));
    }

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @GetMapping("/{threadId}/messages")
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
    public ApiResponse<UnreadCountResponse> getUnreadCount(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language) {
        return ApiResponse.successResponse(chatService.getUnreadCount());
    }

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @PostMapping(value = "/{threadId}/messages/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadAttachmentResponse> uploadImage(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language,
            @PathVariable Long threadId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.successResponse(chatService.uploadAttachment(threadId, file));
    }

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @PostMapping(value = "/{threadId}/messages/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadAttachmentResponse> uploadFile(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language,
            @PathVariable Long threadId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.successResponse(chatService.uploadFileAttachment(threadId, file));
    }

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @DeleteMapping("/{threadId}")
    public ApiResponse<Map<String, String>> hideThread(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language,
            @PathVariable Long threadId) {
        chatService.hideThread(threadId);
        return ApiResponse.successResponse(Map.of("message", "Thread hidden"));
    }

    @PreAuthorize("hasAnyRole('BUYER', 'SELLER')")
    @PostMapping("/ws-token")
    public ApiResponse<WsTokenResponse> issueWsToken(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") String language) {
        return ApiResponse.successResponse(chatService.issueWsToken());
    }
}
