package com.example.config.clent;
import com.example.dto.application.JobChatRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@FeignClient(name="chat-service",configuration=ChatFeignConfiguration.class)
public interface ChatClient {
    @PostMapping("/internal/chats/jobs")
    Map<String,Long> open(@RequestHeader("X-Jobs-Chat-Token") String token,@RequestBody JobChatRequest request);
}