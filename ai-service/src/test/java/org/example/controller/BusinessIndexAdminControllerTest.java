package org.example.controller;

import org.example.ai.business.index.BusinessIndexer;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BusinessIndexAdminController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "server.domain=http://localhost")
class BusinessIndexAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtAuthenticationConverter converter;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean BusinessIndexer indexer;

    @Test
    void buyerCannotTriggerBusinessReindex() throws Exception {
        mockMvc.perform(post("/api/v1/ai/admin/business-reindex").with(jwtFor("BUYER")))
                .andExpect(status().isForbidden());
        verify(indexer, never()).triggerAsyncReindex();
    }

    @Test
    void adminCanTriggerBusinessReindex() throws Exception {
        when(indexer.triggerAsyncReindex()).thenReturn(true);
        mockMvc.perform(post("/api/v1/ai/admin/business-reindex").with(jwtFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.started").value(true));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String role) {
        return jwt().jwt(builder -> builder.subject("sub-1")
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(token -> converter.convert(token).getAuthorities());
    }
}
