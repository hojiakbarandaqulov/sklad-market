package org.example.ai.guardrail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatRateLimitServiceTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock RpmRateLimiter rateLimiter;

    private AiChatRateLimitService service;

    @BeforeEach
    void setUp() {
        service = new AiChatRateLimitService(jdbcTemplate, rateLimiter, 10, 200_000L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesPersistedPerUserOverrideForChatOnly() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user-sub")))
                .thenReturn(List.of(45));
        when(rateLimiter.tryConsume("chat:user-sub", 45)).thenReturn(true);

        assertThat(service.tryConsume("user-sub")).isTrue();

        verify(rateLimiter).tryConsume("chat:user-sub", 45);
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesGlobalDefaultWhenUserHasNoOverride() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user-sub")))
                .thenReturn(List.of());
        when(rateLimiter.tryConsume("chat:user-sub", 10)).thenReturn(true);

        assertThat(service.tryConsume("user-sub")).isTrue();

        verify(rateLimiter).tryConsume("chat:user-sub", 10);
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesPersistedPerUserDailyTokenBudget() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user-sub")))
                .thenReturn(List.of(2_000_000L));

        assertThat(service.dailyTokenBudgetFor("user-sub")).isEqualTo(2_000_000L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesDefaultDailyTokenBudgetWhenUserHasNoOverride() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("user-sub")))
                .thenReturn(List.of());

        assertThat(service.dailyTokenBudgetFor("user-sub")).isEqualTo(200_000L);
    }
}
