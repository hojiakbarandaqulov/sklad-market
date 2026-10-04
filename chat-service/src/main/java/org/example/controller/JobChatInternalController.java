package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.chat.JobChatRequest;
import org.example.service.JobChatService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/chats/jobs")
public class JobChatInternalController {
    private final JobChatService service;

    @PostMapping
    public Map<String, Long> open(@Valid @RequestBody JobChatRequest request) {
        return Map.of("threadId", service.open(request));
    }
}