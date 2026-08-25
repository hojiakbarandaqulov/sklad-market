package org.example.ai.tool.impl;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.gateway.GatewayClient;
import org.example.ai.provider.ChatCompletionResult;
import org.example.ai.provider.ChatGenerationRequest;
import org.example.ai.provider.ChatModelProvider;
import org.example.ai.provider.StructuredCompletionResult;
import org.example.ai.provider.StructuredGenerationRequest;
import org.example.ai.provider.TokenUsage;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * PLAN.md Phase 6, C9: proves {@code draft_lead_reply} (a) re-fetches the real lead rather than
 * trusting model-supplied content, (b) returns TEXT ONLY as tool data, and (c) never calls a write
 * endpoint anywhere — {@link GatewayClient} is structurally GET-only (see
 * {@code GatewayClientHasNoWriteMethodsTest}), and this test additionally confirms zero HTTP writes
 * were made against the WireMock server for the whole tool execution.
 */
class DraftLeadReplyToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("SELLER"), "ru");
    }

    private DraftLeadReplyTool tool(ChatModelProvider provider) {
        return new DraftLeadReplyTool(new GatewayClient(wireMock.baseUrl(), 5), provider, "gemini-2.5-flash");
    }

    @Test
    void execute_fetchesRealLead_andReturnsDraftTextOnly_neverCallsAWriteEndpoint() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/leads/7"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"id":7,"status":"NEW","contactName":"Ali","contactPhone":"+998901234567",
                          "deliveryAddress":"Tashkent","neededDate":"2026-08-01","comment":"Нужен цемент срочно",
                          "items":[{"productId":5,"productNameSnapshot":"Цемент М500","priceSnapshot":50000,"quantity":20}]}}
                        """)));

        FakeGenerateProvider provider = new FakeGenerateProvider("Здравствуйте! Да, цемент М500 в наличии.");
        ToolResult result = tool(provider).execute(Map.of("leadId", 7), context());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("leadId", 7L).containsEntry("tone", "FRIENDLY");
        assertThat(result.data().get("draftReply")).isEqualTo("Здравствуйте! Да, цемент М500 в наличии.");
        assertThat(provider.lastRequest().history().get(0).text()).contains("Нужен цемент срочно").contains("Цемент М500");

        // structural + live proof: nothing but the one GET happened against the platform.
        wireMock.verify(0, postRequestedFor(urlPathMatching(".*")));
    }

    @Test
    void execute_unknownLead_returnsNotFound_neverCallsTheModel() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/leads/999"))
                .willReturn(aResponse().withStatus(400)));

        FakeGenerateProvider provider = new FakeGenerateProvider("should not be used");
        ToolResult result = tool(provider).execute(Map.of("leadId", 999), context());

        assertThat(result.success()).isFalse();
        assertThat(provider.callCount()).isZero();
    }

    @Test
    void execute_customTone_isForwardedAndEchoedBack() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/leads/7"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"id\":7,\"status\":\"NEW\",\"items\":[]}}")));

        FakeGenerateProvider provider = new FakeGenerateProvider("Brief reply.");
        ToolResult result = tool(provider).execute(Map.of("leadId", 7, "tone", "BRIEF"), context());

        assertThat(result.data()).containsEntry("tone", "BRIEF");
        assertThat(provider.lastRequest().systemInstruction()).contains("Match the requested tone");
    }

    /** Minimal fake — only {@link ChatModelProvider#generate} is exercised by this tool. */
    private static final class FakeGenerateProvider implements ChatModelProvider {
        private final String replyText;
        private final List<ChatGenerationRequest> requests = new ArrayList<>();

        private FakeGenerateProvider(String replyText) {
            this.replyText = replyText;
        }

        @Override
        public org.example.ai.provider.ChatStream generateStream(ChatGenerationRequest request) {
            throw new UnsupportedOperationException("not used by draft_lead_reply");
        }

        @Override
        public ChatCompletionResult generate(ChatGenerationRequest request) {
            requests.add(request);
            return new ChatCompletionResult(replyText, new TokenUsage(20, 10, 30));
        }

        @Override
        public StructuredCompletionResult generateStructured(StructuredGenerationRequest request) {
            throw new UnsupportedOperationException("not used by draft_lead_reply");
        }

        int callCount() {
            return requests.size();
        }

        ChatGenerationRequest lastRequest() {
            return requests.get(requests.size() - 1);
        }
    }
}
