package org.example.controller;

import org.example.ai.guardrail.AiChatRateLimitService;
import org.example.config.SecurityConfig;
import org.example.dto.AiUserRateLimitDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiRateLimitAdminController.class)
@Import({SecurityConfig.class, org.example.exception.GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.domain=http://localhost")
class AiRateLimitAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtAuthenticationConverter converter;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean AiChatRateLimitService rateLimitService;

    @Test
    void buyerCannotReadAiChatLimits() throws Exception {
        mockMvc.perform(get("/api/v1/ai/admin/rate-limits").with(jwtFor("BUYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadAiChatLimits() throws Exception {
        when(rateLimitService.list()).thenReturn(List.of(
                new AiUserRateLimitDto(
                        "sub-1", "buyer@example.com", 25, 25,
                        1_000_000L, 1_000_000L, 100_000L, 900_000L, Instant.EPOCH)));

        mockMvc.perform(get("/api/v1/ai/admin/rate-limits").with(jwtFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userSub").value("sub-1"))
                .andExpect(jsonPath("$.data[0].requestsPerMinute").value(25))
                .andExpect(jsonPath("$.data[0].remainingTokensToday").value(900000));
    }

    @Test
    void superAdminCanUpdateOneUsersChatLimit() throws Exception {
        when(rateLimitService.update("sub-1", 60, 2_000_000L)).thenReturn(
                new AiUserRateLimitDto(
                        "sub-1", "buyer@example.com", 60, 60,
                        2_000_000L, 2_000_000L, 0L, 2_000_000L, Instant.EPOCH));

        mockMvc.perform(put("/api/v1/ai/admin/rate-limits/sub-1")
                        .with(jwtFor("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestsPerMinute\":60,\"dailyTokenBudget\":2000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectiveRequestsPerMinute").value(60))
                .andExpect(jsonPath("$.data.effectiveDailyTokenBudget").value(2000000));

        verify(rateLimitService).update("sub-1", 60, 2_000_000L);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String role) {
        return jwt().jwt(builder -> builder.subject("admin-sub")
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(token -> converter.convert(token).getAuthorities());
    }
}
