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

/** PLAN.md Phase 6, C9: mirrors {@link DraftLeadReplyToolTest} for the chat-thread path. */
class DraftChatReplyToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("SELLER"), "ru");
    }

    private DraftChatReplyTool tool(ChatModelProvider provider) {
        return new DraftChatReplyTool(new GatewayClient(wireMock.baseUrl(), 5), provider, "gemini-2.5-flash");
    }

    @Test
    void execute_fetchesRecentMessages_andReturnsDraftTextOnly_neverCallsAWriteEndpoint() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/chats/12/messages"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"items":[
                          {"id":1,"thread_id":12,"sender_id":3,"sender_type":"buyer","body":"Есть в наличии?","sent_at":"2026-07-15T10:00:00"}
                        ],"meta":{"total":1,"page":1,"perPage":10,"totalPages":1}}}
                        """)));

        FakeGenerateProvider provider = new FakeGenerateProvider("Да, есть в наличии.");
        ToolResult result = tool(provider).execute(Map.of("threadId", 12), context());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("threadId", 12L).containsEntry("tone", "FRIENDLY");
        assertThat(result.data().get("draftReply")).isEqualTo("Да, есть в наличии.");
        assertThat(provider.lastRequest().history().get(0).text()).contains("Есть в наличии?");
        wireMock.verify(0, postRequestedFor(urlPathMatching(".*")));
    }

    @Test
    void execute_emptyThread_returnsNotFound_neverCallsTheModel() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/chats/12/messages"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"items\":[],\"meta\":{\"total\":0,\"page\":1,\"perPage\":10,\"totalPages\":0}}}")));

        FakeGenerateProvider provider = new FakeGenerateProvider("should not be used");
        ToolResult result = tool(provider).execute(Map.of("threadId", 12), context());

        assertThat(result.success()).isFalse();
        assertThat(provider.callCount()).isZero();
    }

    private static final class FakeGenerateProvider implements ChatModelProvider {
        private final String replyText;
        private final List<ChatGenerationRequest> requests = new ArrayList<>();

        private FakeGenerateProvider(String replyText) {
            this.replyText = replyText;
        }

        @Override
        public org.example.ai.provider.ChatStream generateStream(ChatGenerationRequest request) {
            throw new UnsupportedOperationException("not used by draft_chat_reply");
        }

        @Override
        public ChatCompletionResult generate(ChatGenerationRequest request) {
            requests.add(request);
            return new ChatCompletionResult(replyText, new TokenUsage(15, 8, 23));
        }

        @Override
        public StructuredCompletionResult generateStructured(StructuredGenerationRequest request) {
            throw new UnsupportedOperationException("not used by draft_chat_reply");
        }

        int callCount() {
            return requests.size();
        }

        ChatGenerationRequest lastRequest() {
            return requests.get(requests.size() - 1);
        }
    }
}
