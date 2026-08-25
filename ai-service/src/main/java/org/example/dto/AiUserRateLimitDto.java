package org.example.dto;

import java.time.Instant;

public record AiUserRateLimitDto(
        String userSub,
        String username,
        Integer requestsPerMinute,
        int effectiveRequestsPerMinute,
        Long dailyTokenBudget,
        long effectiveDailyTokenBudget,
        long usedTokensToday,
        long remainingTokensToday,
        Instant updatedAt) {
}
