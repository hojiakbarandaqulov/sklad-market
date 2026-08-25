package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.dto.PingResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    @GetMapping("/ping")
    public ApiResponse<PingResponse> ping() {
        return ApiResponse.successResponse(new PingResponse("ai-service", "ok", Instant.now()));
    }
}
