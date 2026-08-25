package org.example.controller;

import org.example.ai.guardrail.AiRoleQuotaService;
import org.example.config.SecurityConfig;
import org.example.dto.AiRoleQuotaDto;
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

@WebMvcTest(controllers = AiRoleQuotaAdminController.class)
@Import({SecurityConfig.class, org.example.exception.GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.domain=http://localhost")
class AiRoleQuotaAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtAuthenticationConverter converter;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean AiRoleQuotaService quotaService;

    @Test
    void unauthenticatedCallerCannotReadPolicies() throws Exception {
        mockMvc.perform(get("/api/v1/ai/admin/role-quotas"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buyerCannotReadPolicies() throws Exception {
        mockMvc.perform(get("/api/v1/ai/admin/role-quotas").with(jwtFor("BUYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadPolicies() throws Exception {
        when(quotaService.listPolicies()).thenReturn(List.of(
                new AiRoleQuotaDto("BUYER", 120, 500, Instant.EPOCH, null)));

        mockMvc.perform(get("/api/v1/ai/admin/role-quotas").with(jwtFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].roleName").value("BUYER"))
                .andExpect(jsonPath("$.data[0].hourlyRequestLimit").value(120))
                .andExpect(jsonPath("$.data[0].dailyRequestLimit").value(500));
    }

    @Test
    void superAdminCanCreateFuturePremiumPolicy() throws Exception {
        when(quotaService.updatePolicy("PREMIUM", 900, 9_000, "admin-sub")).thenReturn(
                new AiRoleQuotaDto("PREMIUM", 900, 9_000, Instant.EPOCH, "admin-sub"));

        mockMvc.perform(put("/api/v1/ai/admin/role-quotas/PREMIUM")
                        .with(jwtFor("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hourlyRequestLimit\":900,\"dailyRequestLimit\":9000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleName").value("PREMIUM"))
                .andExpect(jsonPath("$.data.dailyRequestLimit").value(9000));

        verify(quotaService).updatePolicy("PREMIUM", 900, 9_000, "admin-sub");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String role) {
        return jwt().jwt(builder -> builder.subject("admin-sub")
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(token -> converter.convert(token).getAuthorities());
    }
}
