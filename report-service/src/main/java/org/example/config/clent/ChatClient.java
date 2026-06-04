package org.example.config.clent;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "chat-service")
public interface ChatClient {

    @PutMapping("/internal/chats/{id}/block")
    void blockThread(@PathVariable("id") Long id);

}
