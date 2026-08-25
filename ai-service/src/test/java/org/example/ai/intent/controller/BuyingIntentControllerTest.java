package org.example.ai.intent.controller;

import org.example.ai.intent.service.BuyingIntentService;
import org.example.ai.guardrail.RpmRateLimiter;
import org.example.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BuyingIntentController.class)
@Import({SecurityConfig.class, org.example.exception.GlobalExceptionHandler.class})
@TestPropertySource(properties = "server.domain=http://localhost")
class BuyingIntentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private BuyingIntentService service;

    @MockBean
    private RpmRateLimiter rateLimiter;

    @BeforeEach
    void allowRequests() {
        when(rateLimiter.tryConsume(anyString())).thenReturn(true);
    }

    @Test
    void create_noJwt_is401() throws Exception {
        mockMvc.perform(post("/api/v1/ai/buying-intents")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_seller_is403() throws Exception {
        mockMvc.perform(post("/api/v1/ai/buying-intents").with(withRole("seller-sub", "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_buyer_usesJwtSubjectAsOwner() throws Exception {
        mockMvc.perform(post("/api/v1/ai/buying-intents").with(withRole("buyer-sub", "BUYER"))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(service).createDraft(eq("buyer-sub"), any());
    }

    @Test
    void search_buyer_is403_butSellerIs200() throws Exception {
        mockMvc.perform(get("/api/v1/ai/buying-intents/search").with(withRole("buyer-sub", "BUYER")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/ai/buying-intents/search").with(withRole("seller-sub", "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void publish_requiresExplicitTrueSellerVisibilityConsent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/ai/buying-intents/" + id + "/publish")
                        .with(withRole("buyer-sub", "BUYER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicationConsent\":false}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).publish(anyString(), any(), eq(false));
    }

    @Test
    void create_rejectsNumericValuesThatCannotFitTheDatabaseColumns() throws Exception {
        String excessiveScale = validBody().replace("\"quantity\":500", "\"quantity\":0.0001");

        mockMvc.perform(post("/api/v1/ai/buying-intents").with(withRole("buyer-sub", "BUYER"))
                        .contentType(MediaType.APPLICATION_JSON).content(excessiveScale))
                .andExpect(status().isBadRequest());

        verify(service, never()).createDraft(anyString(), any());
    }

    @Test
    void writeRateLimit_isPerUserAndReturns429BeforeMutation() throws Exception {
        when(rateLimiter.tryConsume("buying-intent-write:buyer-sub")).thenReturn(false);

        mockMvc.perform(post("/api/v1/ai/buying-intents").with(withRole("buyer-sub", "BUYER"))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isTooManyRequests());

        verify(service, never()).createDraft(anyString(), any());
    }

    @Test
    void sellerSearchRateLimit_isPerUserAndReturns429BeforeRanking() throws Exception {
        when(rateLimiter.tryConsume("buying-intent-search:seller-sub")).thenReturn(false);

        mockMvc.perform(get("/api/v1/ai/buying-intents/search")
                        .with(withRole("seller-sub", "SELLER")))
                .andExpect(status().isTooManyRequests());

        verify(service, never()).searchPublished(any(), any(), any(), any());
    }

    private RequestPostProcessor withRole(String subject, String role) {
        return jwt().jwt(b -> b.subject(subject).claim("realm_access", Map.of("roles", List.of(role))))
                .authorities(j -> jwtAuthenticationConverter.convert(j).getAuthorities());
    }

    private String validBody() {
        return """
                {
                  "category":"Cement",
                  "region":"Tashkent",
                  "needText":"Need M500 cement",
                  "quantity":500,
                  "quantityUnit":"kg",
                  "budgetMin":1000000,
                  "budgetMax":50000000,
                  "currency":"UZS",
                  "expiresAt":"2030-08-20T10:00:00Z"
                }
                """;
    }
}
