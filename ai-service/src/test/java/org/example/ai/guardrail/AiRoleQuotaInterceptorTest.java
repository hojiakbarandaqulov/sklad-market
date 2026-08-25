package org.example.ai.guardrail;

import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiRoleQuotaInterceptorTest {

    private AiRoleQuotaService quotaService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FixtureController.invoked = false;
        quotaService = mock(AiRoleQuotaService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FixtureController())
                .addInterceptors(new AiRoleQuotaInterceptor(quotaService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-sub")
                .issuedAt(Instant.EPOCH)
                .expiresAt(Instant.parse("2099-01-01T00:00:00Z"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of(
                new SimpleGrantedAuthority("ROLE_BUYER"),
                new SimpleGrantedAuthority("ROLE_PREMIUM"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void protectedEndpointConsumesLiveRoleQuotaBeforeController() throws Exception {
        mockMvc.perform(get("/protected"))
                .andExpect(status().isOk());

        verify(quotaService).consume("user-sub", Set.of("BUYER", "PREMIUM"));
    }

    @Test
    void quotaDenialIsTyped429AndControllerDoesNotRun() throws Exception {
        org.mockito.Mockito.doThrow(new AiChatException(AiErrorCode.RATE_LIMITED, "Hourly AI request limit reached."))
                .when(quotaService).consume("user-sub", Set.of("BUYER", "PREMIUM"));

        mockMvc.perform(get("/protected"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-AI-Error-Code", "rate_limited"))
                .andExpect(jsonPath("$.success").value(false));

        org.assertj.core.api.Assertions.assertThat(FixtureController.invoked).isFalse();
    }

    @Test
    void unprotectedEndpointDoesNotConsumeQuota() throws Exception {
        mockMvc.perform(get("/unprotected"))
                .andExpect(status().isOk());

        verify(quotaService, never()).consume("user-sub", Set.of("BUYER", "PREMIUM"));
    }

    @RestController
    static class FixtureController {
        static boolean invoked;

        @GetMapping("/protected")
        @AiQuotaProtected
        ResponseEntity<Void> protectedEndpoint() {
            invoked = true;
            return ResponseEntity.ok().build();
        }

        @GetMapping("/unprotected")
        ResponseEntity<Void> unprotectedEndpoint() {
            return ResponseEntity.ok().build();
        }
    }
}
