package org.example.dto;

public record UpdateAiUserRateLimitRequest(Integer requestsPerMinute, Long dailyTokenBudget) {
}
