package org.example.controller;

import org.example.ai.embedding.EmbeddingSearchService;
import org.example.ai.observability.AiMetrics;
import org.example.config.SecurityConfig;
import org.example.dto.SearchResultItem;
import org.example.exception.AiNotFoundException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiSearchController.class)
@Import({SecurityConfig.class, org.example.exception.GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.domain=http://localhost")
class AiSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private EmbeddingSearchService searchService;

    @MockBean
    private AiMetrics metrics;

    private org.springframework.test.web.servlet.request.RequestPostProcessor buyer() {
        return jwt().jwt(b -> b.claim("realm_access", Map.of("roles", List.of("BUYER"))))
                .authorities(j -> jwtAuthenticationConverter.convert(j).getAuthorities());
    }

    @Test
    void search_withoutJwt_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ai/search").param("q", "cement")).andExpect(status().isUnauthorized());
    }

    @Test
    void similar_withoutJwt_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ai/similar/5")).andExpect(status().isUnauthorized());
    }

    @Test
    void search_withJwt_returnsEnvelopedResults() throws Exception {
        when(searchService.search(eq("рис"), any())).thenReturn(List.of(
                new SearchResultItem(7L, "rice-basmati", "Basmati guruch", 3L, 1L, 9000.0, "UZS", 0.83)));

        mockMvc.perform(get("/api/v1/ai/search").param("q", "рис").with(buyer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.query").value("рис"))
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.items[0].slug").value("rice-basmati"))
                .andExpect(jsonPath("$.data.items[0].score").value(0.83));
    }

    @Test
    void similar_withJwt_returnsEnvelopedResults() throws Exception {
        when(searchService.similar(eq(7L), any())).thenReturn(List.of(
                new SearchResultItem(8L, "rice-jasmine", "Jasmin guruch", 3L, 1L, 8000.0, "UZS", 0.91)));

        mockMvc.perform(get("/api/v1/ai/similar/7").with(buyer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId").value(7))
                .andExpect(jsonPath("$.data.items[0].slug").value("rice-jasmine"));
    }

    @Test
    void similar_unknownProduct_isNotFound() throws Exception {
        when(searchService.similar(eq(404L), any())).thenThrow(new AiNotFoundException("Product not found in the index"));

        mockMvc.perform(get("/api/v1/ai/similar/404").with(buyer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void search_isNotSubjectToChatRequestLimits() throws Exception {
        when(searchService.search(eq("cement"), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/ai/search").param("q", "cement").with(buyer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        verify(searchService).search(eq("cement"), any());
    }
}
