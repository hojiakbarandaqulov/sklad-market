package org.example.controller;

import org.example.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "server.domain=http://localhost")
class AiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void pingWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ai/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pingWithJwtReturnsEnvelopedResponse() throws Exception {
        mockMvc.perform(get("/api/v1/ai/ping")
                        .with(jwt()
                                .jwt(builder -> builder.claim("realm_access", Map.of("roles", List.of("BUYER"))))
                                .authorities(jwt -> jwtAuthenticationConverter.convert(jwt).getAuthorities())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.service").value("ai-service"))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.time").exists());
    }
}
