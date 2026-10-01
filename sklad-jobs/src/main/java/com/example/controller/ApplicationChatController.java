package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.application.ApplicationChatDTO;
import com.example.service.ApplicationChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/applications")
public class ApplicationChatController {
    private final ApplicationChatService service;

    @PostMapping("/{id}/chat")
    @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    public ApiResponse<ApplicationChatDTO> open(@PathVariable Long id) {
        return ApiResponse.successResponse(service.open(id));
    }
}