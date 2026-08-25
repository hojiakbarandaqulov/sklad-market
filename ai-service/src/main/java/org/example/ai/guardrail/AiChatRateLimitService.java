package org.example.ai.guardrail;

import org.example.dto.AiUserRateLimitDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

/**
 * AI-chat-only limit policy. Search/read endpoints deliberately do not use this service, so
 * catalog discovery can degrade independently without consuming a user's chat allowance.
 */
@Service
public class AiChatRateLimitService {

    public static final int MAX_REQUESTS_PER_MINUTE = 10_000;
    public static final long MAX_DAILY_TOKEN_BUDGET = 100_000_000L;

    private static final Logger log = LoggerFactory.getLogger(AiChatRateLimitService.class);

    private final JdbcTemplate jdbcTemplate;
    private final RpmRateLimiter rateLimiter;
    private final int defaultRequestsPerMinute;
    private final long defaultDailyTokenBudget;

    public AiChatRateLimitService(
            JdbcTemplate jdbcTemplate,
            RpmRateLimiter rateLimiter,
            @Value("${ai.limits.rate-limit-rpm:10}") int defaultRequestsPerMinute,
            @Value("${ai.limits.daily-token-budget:200000}") long defaultDailyTokenBudget) {
        this.jdbcTemplate = jdbcTemplate;
        this.rateLimiter = rateLimiter;
        this.defaultRequestsPerMinute = Math.max(defaultRequestsPerMinute, 0);
        this.defaultDailyTokenBudget = Math.max(defaultDailyTokenBudget, 0L);
    }

    public boolean tryConsume(String userSub) {
        int effectiveLimit = defaultRequestsPerMinute;
        try {
            List<Integer> limits = jdbcTemplate.query(
                    "SELECT requests_per_minute FROM ai_user_rate_limit WHERE user_sub = ?",
                    (rs, rowNum) -> {
                        Integer override = (Integer) rs.getObject("requests_per_minute");
                        return override == null ? defaultRequestsPerMinute : override;
                    },
                    userSub);
            if (!limits.isEmpty()) effectiveLimit = limits.get(0);
        } catch (DataAccessException e) {
            // The admin-control table is optional operational state. A temporary database issue
            // must not take down chat; retain the configured global guardrail as the safe fallback.
            log.warn("Could not read the per-user AI chat limit; using the configured default", e);
        }
        return rateLimiter.tryConsume("chat:" + userSub, effectiveLimit);
    }

    public long dailyTokenBudgetFor(String userSub) {
        try {
            List<Long> budgets = jdbcTemplate.query(
                    "SELECT daily_token_budget FROM ai_user_rate_limit WHERE user_sub = ?",
                    (rs, rowNum) -> {
                        Long override = (Long) rs.getObject("daily_token_budget");
                        return override == null ? defaultDailyTokenBudget : override;
                    },
                    userSub);
            return budgets.isEmpty() ? defaultDailyTokenBudget : budgets.get(0);
        } catch (DataAccessException e) {
            log.warn("Could not read the per-user AI token budget; using the configured default", e);
            return defaultDailyTokenBudget;
        }
    }

    public void registerUser(String userSub, String username) {
        String cleanUsername = cleanUsername(username);
        try {
            jdbcTemplate.update("""
                    INSERT INTO ai_user_rate_limit (user_sub, username)
                    VALUES (?, ?)
                    ON CONFLICT (user_sub) DO UPDATE
                    SET username = COALESCE(EXCLUDED.username, ai_user_rate_limit.username)
                    """, userSub, cleanUsername);
        } catch (DataAccessException e) {
            // Profile registration only improves the admin UI. It must never block a chat turn.
            log.warn("Could not register an AI chat user for limit administration", e);
        }
    }

    public List<AiUserRateLimitDto> list() {
        return jdbcTemplate.query("""
                        SELECT limits.user_sub,
                               limits.username,
                               limits.requests_per_minute,
                               limits.daily_token_budget,
                               limits.updated_at,
                               COALESCE(ledger.tokens_in, 0) + COALESCE(ledger.tokens_out, 0) AS used_tokens_today
                        FROM ai_user_rate_limit limits
                        LEFT JOIN usage_ledger ledger
                          ON ledger.user_sub = limits.user_sub
                         AND ledger.day = CURRENT_DATE
                        ORDER BY limits.username NULLS LAST, limits.user_sub
                        """,
                (rs, rowNum) -> {
                    Integer rpmOverride = (Integer) rs.getObject("requests_per_minute");
                    Long budgetOverride = (Long) rs.getObject("daily_token_budget");
                    long effectiveBudget = budgetOverride == null ? defaultDailyTokenBudget : budgetOverride;
                    long usedTokensToday = rs.getLong("used_tokens_today");
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    return new AiUserRateLimitDto(
                            rs.getString("user_sub"),
                            rs.getString("username"),
                            rpmOverride,
                            rpmOverride == null ? defaultRequestsPerMinute : rpmOverride,
                            budgetOverride,
                            effectiveBudget,
                            usedTokensToday,
                            Math.max(0L, effectiveBudget - usedTokensToday),
                            updatedAt == null ? null : updatedAt.toInstant());
                });
    }

    public AiUserRateLimitDto update(String userSub, Integer requestsPerMinute, Long dailyTokenBudget) {
        validateUserSub(userSub);
        validateRpmLimit(requestsPerMinute);
        validateDailyTokenBudget(dailyTokenBudget);
        jdbcTemplate.update("""
                INSERT INTO ai_user_rate_limit (user_sub, requests_per_minute, daily_token_budget, updated_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (user_sub) DO UPDATE
                SET requests_per_minute = EXCLUDED.requests_per_minute,
                    daily_token_budget = COALESCE(EXCLUDED.daily_token_budget, ai_user_rate_limit.daily_token_budget),
                    updated_at = now()
                """, userSub, requestsPerMinute, dailyTokenBudget);
        return findRequired(userSub);
    }

    public AiUserRateLimitDto reset(String userSub) {
        validateUserSub(userSub);
        int changed = jdbcTemplate.update("""
                UPDATE ai_user_rate_limit
                SET requests_per_minute = NULL,
                    daily_token_budget = NULL,
                    updated_at = now()
                WHERE user_sub = ?
                """, userSub);
        if (changed == 0) {
            jdbcTemplate.update("INSERT INTO ai_user_rate_limit (user_sub) VALUES (?)", userSub);
        }
        return findRequired(userSub);
    }

    public int defaultRequestsPerMinute() {
        return defaultRequestsPerMinute;
    }

    public long defaultDailyTokenBudget() {
        return defaultDailyTokenBudget;
    }

    private AiUserRateLimitDto findRequired(String userSub) {
        return list().stream()
                .filter(item -> item.userSub().equals(userSub))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("AI user limit was not saved"));
    }

    private void validateRpmLimit(Integer requestsPerMinute) {
        if (requestsPerMinute == null
                || requestsPerMinute < 0
                || requestsPerMinute > MAX_REQUESTS_PER_MINUTE) {
            throw new IllegalArgumentException(
                    "requestsPerMinute must be between 0 and " + MAX_REQUESTS_PER_MINUTE);
        }
    }

    private void validateDailyTokenBudget(Long dailyTokenBudget) {
        if (dailyTokenBudget != null
                && (dailyTokenBudget < 0 || dailyTokenBudget > MAX_DAILY_TOKEN_BUDGET)) {
            throw new IllegalArgumentException(
                    "dailyTokenBudget must be between 0 and " + MAX_DAILY_TOKEN_BUDGET);
        }
    }

    private void validateUserSub(String userSub) {
        if (userSub == null || userSub.isBlank() || userSub.length() > 255) {
            throw new IllegalArgumentException("userSub must contain 1 to 255 characters");
        }
    }

    private String cleanUsername(String username) {
        if (username == null || username.isBlank()) return null;
        String clean = username.trim();
        return clean.length() <= 255 ? clean : clean.substring(0, 255);
    }
}
