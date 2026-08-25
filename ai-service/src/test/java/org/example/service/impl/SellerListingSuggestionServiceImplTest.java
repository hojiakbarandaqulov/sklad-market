package org.example.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.ai.gateway.GatewayClient;
import org.example.ai.guardrail.RpmRateLimiter;
import org.example.ai.guardrail.TokenBudgetGuard;
import org.example.ai.guardrail.UsageLedgerService;
import org.example.ai.provider.ChatCompletionResult;
import org.example.ai.provider.ChatGenerationRequest;
import org.example.ai.provider.ChatModelProvider;
import org.example.ai.provider.ChatStream;
import org.example.ai.provider.StructuredCompletionResult;
import org.example.ai.provider.StructuredGenerationRequest;
import org.example.ai.provider.TokenUsage;
import org.example.dto.SuggestListingRequest;
import org.example.dto.SuggestListingResponse;
import org.example.dto.SuggestedAttributeDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PLAN.md Phase 6, C8: proves the STRICT attribute validation contract end-to-end — a
 * model-proposed SELECT value not in the real {@code optionsJson} and a NUMBER value that isn't
 * actually numeric are both dropped (never passed through), and a dropped REQUIRED attribute shows
 * up in {@code missingRequired}, never silently disappears. Also covers the guardrails (RPM/budget)
 * and input caps this endpoint enforces exactly like the chat turn does (PLAN.md §4.2 item 6).
 */
class SellerListingSuggestionServiceImplTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private RpmRateLimiter allowingRateLimiter() {
        RpmRateLimiter limiter = mock(RpmRateLimiter.class);
        when(limiter.tryConsume(anyString())).thenReturn(true);
        return limiter;
    }

    private TokenBudgetGuard allowingBudgetGuard() {
        TokenBudgetGuard guard = mock(TokenBudgetGuard.class);
        when(guard.hasRemainingBudget(anyString())).thenReturn(true);
        return guard;
    }

    private SellerListingSuggestionServiceImpl service(ChatModelProvider provider, RpmRateLimiter limiter, TokenBudgetGuard budgetGuard) {
        return new SellerListingSuggestionServiceImpl(
                new GatewayClient(wireMock.baseUrl(), 5), provider, limiter, budgetGuard,
                mock(UsageLedgerService.class), new ObjectMapper(),
                "gemini-2.5-pro", 2000, 4, 6_000_000L);
    }

    private void stubCategories() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/categories"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"content":[
                          {"id":1,"nameRu":"Цемент","slug":"cement","isActive":true},
                          {"id":2,"nameRu":"Рис","slug":"rice","isActive":true}
                        ]}}
                        """)));
    }

    private void stubAttributeSchema() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/categories/cement/attributes"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":[
                          {"id":1,"code":"grade","label":"Grade","dataType":"SELECT","isRequired":true,"isFilterable":true,"optionsJson":"[\\"M400\\",\\"M500\\"]","sortOrder":1},
                          {"id":2,"code":"weightKg","label":"Weight (kg)","dataType":"NUMBER","isRequired":false,"isFilterable":false,"optionsJson":null,"sortOrder":2},
                          {"id":3,"code":"isOrganic","label":"Organic","dataType":"BOOLEAN","isRequired":false,"isFilterable":false,"optionsJson":null,"sortOrder":3}
                        ]}
                        """)));
    }

    @Test
    void suggest_dropsInvalidValues_andReportsMissingRequired_forOnlyTheDroppedRequiredField() {
        stubCategories();
        stubAttributeSchema();
        SequencedStructuredProvider provider = new SequencedStructuredProvider(List.of(
                "{\"categorySlug\":\"cement\",\"confidence\":0.9}",
                "{\"grade\":\"M999\",\"weightKg\":\"fifty\",\"isOrganic\":true}"));

        SuggestListingResponse response = service(provider, allowingRateLimiter(), allowingBudgetGuard())
                .suggest("seller-1", "jwt", "RU", requestOf("Цемент М500 в мешках по 50кг, органический"));

        assertThat(response.getCategory().getSlug()).isEqualTo("cement");
        assertThat(response.getCategoryConfidence()).isEqualTo(0.9);
        assertThat(response.getAttributes()).extracting(SuggestedAttributeDto::getCode).containsExactly("isOrganic");
        assertThat(response.getAttributes().get(0).getValue()).isEqualTo(true);
        // "grade" was required AND its proposed value (M999) failed optionsJson membership -> dropped -> missing.
        assertThat(response.getMissingRequired()).containsExactly("grade");
    }

    @Test
    void suggest_hallucinatedCategorySlug_neverFetchesAttributeSchema_returnsNullCategory() {
        stubCategories();
        SequencedStructuredProvider provider = new SequencedStructuredProvider(List.of(
                "{\"categorySlug\":\"not-a-real-category\",\"confidence\":0.5}"));

        SuggestListingResponse response = service(provider, allowingRateLimiter(), allowingBudgetGuard())
                .suggest("seller-1", "jwt", "RU", requestOf("something vague"));

        assertThat(response.getCategory()).isNull();
        assertThat(response.getAttributes()).isEmpty();
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/categories/not-a-real-category/attributes")));
    }

    @Test
    void suggest_rateLimited_throwsBeforeAnyGatewayOrModelCall() {
        RpmRateLimiter limiter = mock(RpmRateLimiter.class);
        when(limiter.tryConsume(anyString())).thenReturn(false);
        SequencedStructuredProvider provider = new SequencedStructuredProvider(List.of());

        assertThatThrownBy(() -> service(provider, limiter, allowingBudgetGuard())
                .suggest("seller-1", "jwt", "RU", requestOf("cement")))
                .isInstanceOf(AiChatException.class)
                .satisfies(e -> assertThat(((AiChatException) e).code()).isEqualTo(AiErrorCode.RATE_LIMITED));

        assertThat(provider.callCount()).isZero();
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/categories")));
    }

    @Test
    void suggest_budgetExhausted_throws() {
        TokenBudgetGuard guard = mock(TokenBudgetGuard.class);
        when(guard.hasRemainingBudget(anyString())).thenReturn(false);

        assertThatThrownBy(() -> service(new SequencedStructuredProvider(List.of()), allowingRateLimiter(), guard)
                .suggest("seller-1", "jwt", "RU", requestOf("cement")))
                .isInstanceOf(AiChatException.class)
                .satisfies(e -> assertThat(((AiChatException) e).code()).isEqualTo(AiErrorCode.BUDGET_EXCEEDED));
    }

    @Test
    void suggest_blankDescription_throwsInvalidInput() {
        assertThatThrownBy(() -> service(new SequencedStructuredProvider(List.of()), allowingRateLimiter(), allowingBudgetGuard())
                .suggest("seller-1", "jwt", "RU", requestOf("   ")))
                .isInstanceOf(AiChatException.class)
                .satisfies(e -> assertThat(((AiChatException) e).code()).isEqualTo(AiErrorCode.INVALID_INPUT));
    }

    @Test
    void suggest_tooManyImages_throwsInvalidInput_beforeAnyGatewayCall() {
        SuggestListingRequest request = requestOf("cement");
        request.setImageIds(List.of("a", "b", "c", "d", "e")); // cap is 4

        assertThatThrownBy(() -> service(new SequencedStructuredProvider(List.of()), allowingRateLimiter(), allowingBudgetGuard())
                .suggest("seller-1", "jwt", "RU", request))
                .isInstanceOf(AiChatException.class)
                .satisfies(e -> assertThat(((AiChatException) e).code()).isEqualTo(AiErrorCode.INVALID_INPUT));
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/attach/open/a")));
    }

    @Test
    void suggest_categoryWithNoConfiguredAttributes_returnsCategoryWithEmptyAttributesAndNote() {
        stubCategories();
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/categories/cement/attributes"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":[]}")));
        SequencedStructuredProvider provider = new SequencedStructuredProvider(List.of(
                "{\"categorySlug\":\"cement\",\"confidence\":0.8}"));

        SuggestListingResponse response = service(provider, allowingRateLimiter(), allowingBudgetGuard())
                .suggest("seller-1", "jwt", "RU", requestOf("cement bags"));

        assertThat(response.getCategory().getSlug()).isEqualTo("cement");
        assertThat(response.getAttributes()).isEmpty();
        assertThat(response.getNotes()).isNotBlank();
        assertThat(provider.callCount()).isEqualTo(1); // never made the (pointless) second, attribute-extraction call
    }

    private SuggestListingRequest requestOf(String description) {
        SuggestListingRequest request = new SuggestListingRequest();
        request.setDescription(description);
        return request;
    }

    /** Returns each stubbed JSON response in order, one per {@code generateStructured} call. */
    private static final class SequencedStructuredProvider implements ChatModelProvider {
        private final List<String> responses;
        private final List<StructuredGenerationRequest> requests = new ArrayList<>();

        private SequencedStructuredProvider(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public ChatStream generateStream(ChatGenerationRequest request) {
            throw new UnsupportedOperationException("not used by suggest-listing");
        }

        @Override
        public ChatCompletionResult generate(ChatGenerationRequest request) {
            throw new UnsupportedOperationException("not used by suggest-listing");
        }

        @Override
        public StructuredCompletionResult generateStructured(StructuredGenerationRequest request) {
            requests.add(request);
            String json = responses.get(requests.size() - 1);
            return new StructuredCompletionResult(json, new TokenUsage(10, 5, 15));
        }

        int callCount() {
            return requests.size();
        }
    }
}
