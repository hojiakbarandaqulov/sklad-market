package org.example.controller;

import org.example.ai.business.dto.BusinessSearchResponse;
import org.example.ai.business.dto.BusinessIndexFreshness;
import org.example.ai.business.dto.SupplierRecommendationResponse;
import org.example.ai.business.service.BusinessSearchService;
import org.example.ai.business.service.SupplierRecommendationService;
import org.example.ai.guardrail.RpmRateLimiter;
import org.example.ai.guardrail.TokenBudgetGuard;
import org.example.ai.guardrail.UsageLedgerService;
import org.example.ai.observability.AiMetrics;
import org.example.ai.tool.CategoryResolver;
import org.example.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.Map;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BusinessDiscoveryController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "server.domain=http://localhost")
class BusinessDiscoveryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtAuthenticationConverter converter;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean BusinessSearchService searchService;
    @MockBean SupplierRecommendationService supplierService;
    @MockBean CategoryResolver categoryResolver;
    @MockBean RpmRateLimiter rateLimiter;
    @MockBean TokenBudgetGuard budgetGuard;
    @MockBean UsageLedgerService usageLedgerService;
    @MockBean AiMetrics metrics;

    @BeforeEach
    void guardsAllow() {
        when(rateLimiter.tryConsume(anyString())).thenReturn(true);
        when(budgetGuard.hasRemainingBudget(anyString())).thenReturn(true);
    }

    @Test
    void businessSearchRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/ai/business-search").param("q", "cement"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedBusinessSearchIsRoleOpenAndReturnsTypedEnvelope() throws Exception {
        when(searchService.search(any(), any(), anyBoolean())).thenReturn(
                new BusinessSearchResponse("cement", 0, List.of(), BusinessSearchService.SCORE_MEANING,
                        new BusinessIndexFreshness(Instant.parse("2026-08-11T00:00:00Z"), false,
                                "product=SUCCESS;company=SUCCESS", "fresh")));

        mockMvc.perform(get("/api/v1/ai/business-search").param("q", "cement")
                        .with(jwtFor("SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.query").value("cement"))
                .andExpect(jsonPath("$.data.indexFreshness.stale").value(false));
        verify(usageLedgerService, never()).recordEmbeddingRequest(anyString(), org.mockito.ArgumentMatchers.eq("cement"));
    }

    @Test
    void exhaustedChatBudgetDoesNotDisableDashboardAiSearch() throws Exception {
        when(budgetGuard.hasRemainingBudget(anyString())).thenReturn(false);
        when(searchService.search(any(), any(), org.mockito.ArgumentMatchers.eq(true))).thenReturn(
                new BusinessSearchResponse("cement", 0, List.of(), BusinessSearchService.SCORE_MEANING,
                        new BusinessIndexFreshness(null, true, "product=NEVER_RUN;company=NEVER_RUN", "fallback")));

        mockMvc.perform(get("/api/v1/ai/business-search").param("q", "cement")
                        .with(jwtFor("BUYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(searchService).search(any(), any(), org.mockito.ArgumentMatchers.eq(true));
        verify(usageLedgerService, never()).recordEmbeddingRequest(anyString(), anyString());
    }

    @Test
    void productPriceFilterRequiresExplicitCurrency() throws Exception {
        mockMvc.perform(get("/api/v1/ai/business-search")
                        .param("q", "cement")
                        .param("minPrice", "100")
                        .with(jwtFor("BUYER")))
                .andExpect(status().isBadRequest());
        verify(searchService, never()).search(any(), any());
    }

    @Test
    void supplierRecommendationIsBuyerOnly() throws Exception {
        mockMvc.perform(post("/api/v1/ai/recommendations/suppliers")
                        .with(jwtFor("SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        verify(supplierService, never()).recommend(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void buyerCanRequestSupplierRecommendations() throws Exception {
        when(supplierService.recommend(any(), any(), any(), any(), any(), any(), any())).thenReturn(
                new SupplierRecommendationResponse("COLD_START", 0, List.of(),
                        BusinessSearchService.SCORE_MEANING, "No guarantees",
                        new BusinessIndexFreshness(null, true, "company=NEVER_RUN", "stale")));

        mockMvc.perform(post("/api/v1/ai/recommendations/suppliers")
                        .with(jwtFor("BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.personalizationSource").value("COLD_START"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor jwtFor(String role) {
        return jwt().jwt(builder -> builder.subject("sub-1")
                        .claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(token -> converter.convert(token).getAuthorities());
    }
}
