package org.example.ai.guardrail;

import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.dto.AiRoleQuotaDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Persistent hourly/daily AI request quotas resolved from the caller's live JWT roles. */
@Service
public class AiRoleQuotaService {

    public static final int MAX_REQUEST_LIMIT = 1_000_000;

    private static final String FALLBACK_ROLE = "USER";
    private static final Pattern ROLE_NAME = Pattern.compile("[A-Z0-9][A-Z0-9_.:-]{0,63}");

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Autowired
    public AiRoleQuotaService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    AiRoleQuotaService(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /**
     * Atomically consumes both windows. If the daily update is denied, the surrounding transaction
     * rolls the hourly increment back, so one rejected request can never consume a partial unit.
     */
    @Transactional
    public void consume(String userSub, Set<String> liveRoles) {
        validateUserSub(userSub);
        EffectiveQuota quota = resolveEffectiveQuota(liveRoles);
        if (quota.hourlyLimit() <= 0) {
            throw rateLimited("Hourly AI request limit reached.");
        }
        if (quota.dailyLimit() <= 0) {
            throw rateLimited("Daily AI request limit reached.");
        }

        Instant now = clock.instant();
        Instant hourStart = now.truncatedTo(ChronoUnit.HOURS);
        Instant dayStart = LocalDate.ofInstant(now, ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();

        if (!tryConsumeWindow(userSub, "HOUR", hourStart, quota.hourlyLimit())) {
            throw rateLimited("Hourly AI request limit reached.");
        }
        if (!tryConsumeWindow(userSub, "DAY", dayStart, quota.dailyLimit())) {
            throw rateLimited("Daily AI request limit reached.");
        }
    }

    public List<AiRoleQuotaDto> listPolicies() {
        return jdbcTemplate.query("""
                        SELECT role_name, hourly_request_limit, daily_request_limit, updated_at, updated_by
                        FROM ai_role_request_quota
                        ORDER BY role_name
                        """,
                (rs, rowNum) -> new AiRoleQuotaDto(
                        rs.getString("role_name"),
                        rs.getInt("hourly_request_limit"),
                        rs.getInt("daily_request_limit"),
                        toInstant(rs.getTimestamp("updated_at")),
                        rs.getString("updated_by")));
    }

    public AiRoleQuotaDto updatePolicy(
            String roleName, Integer hourlyRequestLimit, Integer dailyRequestLimit, String updatedBy) {
        String normalizedRole = normalizeRoleName(roleName);
        validateLimit("hourlyRequestLimit", hourlyRequestLimit);
        validateLimit("dailyRequestLimit", dailyRequestLimit);
        validateUserSub(updatedBy);

        jdbcTemplate.update("""
                INSERT INTO ai_role_request_quota
                    (role_name, hourly_request_limit, daily_request_limit, updated_at, updated_by)
                VALUES (?, ?, ?, now(), ?)
                ON CONFLICT (role_name) DO UPDATE
                SET hourly_request_limit = EXCLUDED.hourly_request_limit,
                    daily_request_limit = EXCLUDED.daily_request_limit,
                    updated_at = now(),
                    updated_by = EXCLUDED.updated_by
                """, normalizedRole, hourlyRequestLimit, dailyRequestLimit, updatedBy);
        return findRequired(normalizedRole);
    }

    private EffectiveQuota resolveEffectiveQuota(Set<String> liveRoles) {
        List<AiRoleQuotaDto> policies = listPolicies();
        Set<String> normalizedRoles = new LinkedHashSet<>();
        if (liveRoles != null) {
            liveRoles.stream()
                    .filter(role -> role != null && !role.isBlank())
                    .map(role -> role.trim().toUpperCase(Locale.ROOT))
                    .forEach(normalizedRoles::add);
        }

        List<AiRoleQuotaDto> matches = policies.stream()
                .filter(policy -> normalizedRoles.contains(policy.roleName()))
                .toList();
        if (matches.isEmpty()) {
            matches = policies.stream()
                    .filter(policy -> FALLBACK_ROLE.equals(policy.roleName()))
                    .toList();
        }
        if (matches.isEmpty()) {
            throw new IllegalStateException("The USER AI role quota policy is missing");
        }

        int hourly = matches.stream().mapToInt(AiRoleQuotaDto::hourlyRequestLimit).max().orElse(0);
        int daily = matches.stream().mapToInt(AiRoleQuotaDto::dailyRequestLimit).max().orElse(0);
        return new EffectiveQuota(hourly, daily);
    }

    private boolean tryConsumeWindow(String userSub, String windowType, Instant windowStart, int limit) {
        int changed = jdbcTemplate.update("""
                INSERT INTO ai_request_quota_usage (user_sub, window_type, window_start, request_count)
                VALUES (?, ?, ?, 1)
                ON CONFLICT (user_sub, window_type, window_start) DO UPDATE
                SET request_count = ai_request_quota_usage.request_count + 1
                WHERE ai_request_quota_usage.request_count < ?
                """, userSub, windowType, Timestamp.from(windowStart), limit);
        return changed == 1;
    }

    private AiRoleQuotaDto findRequired(String roleName) {
        List<AiRoleQuotaDto> result = jdbcTemplate.query("""
                        SELECT role_name, hourly_request_limit, daily_request_limit, updated_at, updated_by
                        FROM ai_role_request_quota
                        WHERE role_name = ?
                        """,
                (rs, rowNum) -> new AiRoleQuotaDto(
                        rs.getString("role_name"),
                        rs.getInt("hourly_request_limit"),
                        rs.getInt("daily_request_limit"),
                        toInstant(rs.getTimestamp("updated_at")),
                        rs.getString("updated_by")),
                roleName);
        return result.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("AI role quota policy was not saved"));
    }

    private String normalizeRoleName(String roleName) {
        String normalized = roleName == null ? "" : roleName.trim().toUpperCase(Locale.ROOT);
        if (!ROLE_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "roleName must contain 1 to 64 letters, numbers, '.', '_', ':' or '-'");
        }
        return normalized;
    }

    private void validateLimit(String field, Integer limit) {
        if (limit == null || limit < 0 || limit > MAX_REQUEST_LIMIT) {
            throw new IllegalArgumentException(field + " must be between 0 and " + MAX_REQUEST_LIMIT);
        }
    }

    private void validateUserSub(String userSub) {
        if (userSub == null || userSub.isBlank() || userSub.length() > 255) {
            throw new IllegalArgumentException("userSub must contain 1 to 255 characters");
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private AiChatException rateLimited(String message) {
        return new AiChatException(AiErrorCode.RATE_LIMITED, message);
    }

    private record EffectiveQuota(int hourlyLimit, int dailyLimit) {
    }
}
