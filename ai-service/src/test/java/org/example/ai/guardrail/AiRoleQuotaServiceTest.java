package org.example.ai.guardrail;

import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.dto.AiRoleQuotaDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRoleQuotaServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-23T10:34:56Z");

    @Mock JdbcTemplate jdbcTemplate;

    private AiRoleQuotaService service;

    @BeforeEach
    void setUp() {
        service = new AiRoleQuotaService(jdbcTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @SuppressWarnings("unchecked")
    void consumesMostPermissiveMatchingLiveRoleInBothPersistentWindows() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(
                policy("USER", 30, 100),
                policy("BUYER", 120, 500),
                policy("PREMIUM", 900, 9_000)));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), anyInt())).thenReturn(1);

        service.consume("user-sub", Set.of("BUYER", "PREMIUM", "default-roles-market-realm"));

        verify(jdbcTemplate).update(anyString(), eq("user-sub"), eq("HOUR"),
                eq(Timestamp.from(Instant.parse("2026-08-23T10:00:00Z"))), eq(900));
        verify(jdbcTemplate).update(anyString(), eq("user-sub"), eq("DAY"),
                eq(Timestamp.from(Instant.parse("2026-08-23T00:00:00Z"))), eq(9_000));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToUserPolicyWhenJwtHasNoConfiguredRole() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of(
                policy("USER", 30, 100), policy("BUYER", 120, 500)));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), anyInt())).thenReturn(1);

        service.consume("user-sub", Set.of("offline_access"));

        verify(jdbcTemplate).update(anyString(), eq("user-sub"), eq("HOUR"), any(Timestamp.class), eq(30));
        verify(jdbcTemplate).update(anyString(), eq("user-sub"), eq("DAY"), any(Timestamp.class), eq(100));
    }

    @Test
    @SuppressWarnings("unchecked")
    void deniedDailyWindowReturnsTypedRateLimit() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of(policy("BUYER", 120, 500)));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), anyInt())).thenReturn(1, 0);

        assertThatThrownBy(() -> service.consume("user-sub", Set.of("BUYER")))
                .isInstanceOfSatisfying(AiChatException.class, error -> {
                    assertThat(error.code()).isEqualTo(AiErrorCode.RATE_LIMITED);
                    assertThat(error.getMessage()).contains("Daily");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void zeroPolicyDisablesRequestsWithoutWritingUsage() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of(policy("SELLER", 0, 0)));

        assertThatThrownBy(() -> service.consume("user-sub", Set.of("SELLER")))
                .isInstanceOf(AiChatException.class);

        verify(jdbcTemplate, org.mockito.Mockito.never())
                .update(anyString(), any(), any(), any(), anyInt());
    }

    private AiRoleQuotaDto policy(String role, int hourly, int daily) {
        return new AiRoleQuotaDto(role, hourly, daily, NOW, null);
    }
}
