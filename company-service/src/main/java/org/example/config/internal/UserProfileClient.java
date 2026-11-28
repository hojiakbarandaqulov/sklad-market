package org.example.config.internal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service")
public interface UserProfileClient {
    @GetMapping("/internal/profiles/{profileId}/summary")
    ProfileSummary summary(@PathVariable("profileId") Long profileId);

    record ProfileSummary(Long id) {
    }
}
