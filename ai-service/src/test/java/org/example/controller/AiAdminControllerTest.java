package org.example.controller;

import org.example.ai.embedding.IndexStatus;
import org.example.ai.embedding.ProductIndexer;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The admin authz matrix (PLAN.md Phase 5): every admin endpoint must be 401 unauthenticated,
 * 403 for a non-admin (BUYER/SELLER), and 200 for ADMIN/SUPER_ADMIN — proving both the JWT
 * requirement (SecurityConfig) and the {@code @PreAuthorize} role gate.
 */
@WebMvcTest(controllers = AiAdminController.class)
@Import({SecurityConfig.class, org.example.exception.GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.domain=http://localhost")
class AiAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ProductIndexer indexer;

    private RequestPostProcessor withRoles(String... roles) {
        return jwt().jwt(b -> b.claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(j -> jwtAuthenticationConverter.convert(j).getAuthorities());
    }

    @Test
    void reindex_noJwt_401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/admin/reindex")).andExpect(status().isUnauthorized());
    }

    @Test
    void reindex_buyer_403() throws Exception {
        mockMvc.perform(post("/api/v1/ai/admin/reindex").with(withRoles("BUYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reindex_seller_403() throws Exception {
        mockMvc.perform(post("/api/v1/ai/admin/reindex").with(withRoles("SELLER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reindex_admin_200() throws Exception {
        when(indexer.triggerAsyncReindex()).thenReturn(true);
        mockMvc.perform(post("/api/v1/ai/admin/reindex").with(withRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.started").value(true));
    }

    @Test
    void reindex_superAdmin_200() throws Exception {
        when(indexer.triggerAsyncReindex()).thenReturn(false);
        mockMvc.perform(post("/api/v1/ai/admin/reindex").with(withRoles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.started").value(false));
    }

    @Test
    void status_noJwt_401() throws Exception {
        mockMvc.perform(get("/api/v1/ai/admin/reindex/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void status_buyer_403() throws Exception {
        mockMvc.perform(get("/api/v1/ai/admin/reindex/status").with(withRoles("BUYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void status_admin_200() throws Exception {
        when(indexer.status()).thenReturn(new IndexStatus(false, 12, null, "SUCCESS", 12, "notes"));
        mockMvc.perform(get("/api/v1/ai/admin/reindex/status").with(withRoles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indexSize").value(12))
                .andExpect(jsonPath("$.data.lastStatus").value("SUCCESS"));
    }
}
