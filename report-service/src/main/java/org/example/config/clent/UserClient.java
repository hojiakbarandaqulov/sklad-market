package org.example.config.clent;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/internal/profiles/stats/blocked-count")
    Long blockedCount();


    @PutMapping("/internal/profiles/{userId}/warning")
    void Warning(@PathVariable("userId") Long userId);

}
