package org.example.controller;

import org.example.config.SecurityConfig;
import org.example.dto.SuggestListingResponse;
import org.example.dto.SuggestedCategoryDto;
import org.example.service.SellerListingSuggestionService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PLAN.md Phase 6, the required "test matrix proving a BUYER token reaches none of it" — REST
 * layer, mirroring {@code AiAdminControllerTest}'s pattern: 401 unauthenticated / 403 non-seller /
 * 200 seller, proving the {@code @PreAuthorize} half of the dual-layer enforcement (the tool
 * registry half is in {@code SellerAdminToolRoleGatingTest}).
 */
@WebMvcTest(controllers = AiSellerController.class)
@Import({SecurityConfig.class, org.example.exception.GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.domain=http://localhost")
class AiSellerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private SellerListingSuggestionService suggestionService;

    private RequestPostProcessor withRoles(String... roles) {
        return jwt().jwt(b -> b.claim("realm_access", Map.of("roles", List.of(roles))))
                .authorities(j -> jwtAuthenticationConverter.convert(j).getAuthorities());
    }

    @Test
    void suggestListing_noJwt_401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/seller/suggest-listing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"cement\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suggestListing_buyer_403() throws Exception {
        mockMvc.perform(post("/api/v1/ai/seller/suggest-listing").with(withRoles("BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"cement\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void suggestListing_admin_403() throws Exception {
        // ADMIN is a different persona entirely — this endpoint is SELLER-only, not "any elevated role".
        mockMvc.perform(post("/api/v1/ai/seller/suggest-listing").with(withRoles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"cement\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void suggestListing_seller_200() throws Exception {
        when(suggestionService.suggest(any(), any(), any(), any())).thenReturn(SuggestListingResponse.builder()
                .category(SuggestedCategoryDto.builder().slug("cement").name("Цемент").build())
                .categoryConfidence(0.9)
                .attributes(List.of())
                .missingRequired(List.of())
                .build());

        mockMvc.perform(post("/api/v1/ai/seller/suggest-listing").with(withRoles("SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Цемент М500 в мешках по 50кг\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.category.slug").value("cement"));
    }
}
