package org.example.controller;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.chat.JobChatRequest;
import org.example.service.JobChatService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController @RequiredArgsConstructor
@RequestMapping("/internal/chats/jobs")
public class JobChatInternalController {
    private final JobChatService service;
    @Value("${jobs.chat.internal-token:}") private String token;

    @PostMapping
    public Map<String,Long> open(@RequestHeader(value="X-Jobs-Chat-Token",required=false) String supplied,
                               @Valid @RequestBody JobChatRequest request) {
        if(token==null || token.isBlank() || supplied==null ||
            !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Internal service credentials required");
        return Map.of("threadId",service.open(request));
    }
}