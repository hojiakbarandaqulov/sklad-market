package org.example.client;

import org.example.client.dto.UserSummaryResponse;
import org.example.config.FeignClientConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "user-service",
        url = "${services.user.url}",
        configuration = FeignClientConfig.class
)
public interface UserClient {

    @GetMapping("/internal/profiles/{userId}/summary")
    UserSummaryResponse getSummary(@PathVariable Long userId);
}
