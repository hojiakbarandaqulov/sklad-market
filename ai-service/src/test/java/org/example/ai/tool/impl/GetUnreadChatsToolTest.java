package org.example.ai.tool.impl;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.gateway.GatewayClient;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GetUnreadChatsToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private GetUnreadChatsTool tool() {
        return new GetUnreadChatsTool(new GatewayClient(wireMock.baseUrl(), 5));
    }

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("BUYER"), "ru");
    }

    @Test
    void execute_filtersOutThreadsWithNoUnreadMessages_andForwardsSnakeCasePerPageParam() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/chats"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"items":[
                          {"thread_id":1,"other_party":{"display_name":"Acme LLC"},"last_message":{"body":"Hello"},"unread_count":2},
                          {"thread_id":2,"other_party":{"display_name":"Beta LLC"},"last_message":{"body":"Read already"},"unread_count":0}
                        ],"meta":{"total":2,"page":1,"perPage":20,"totalPages":1}}}
                        """)));

        ToolResult result = tool().execute(Map.of(), context());

        assertThat(result.success()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unread = (List<Map<String, Object>>) result.data().get("unreadThreads");
        assertThat(unread).hasSize(1);
        assertThat(unread.get(0)).containsEntry("otherPartyName", "Acme LLC").containsEntry("unreadCount", 2L);
        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/chats")).withQueryParam("per_page", equalTo("20")));
    }

    @Test
    void execute_noUnread_returnsEmptyList() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/chats"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"items\":[],\"meta\":{\"total\":0,\"page\":1,\"perPage\":20,\"totalPages\":0}}}")));

        ToolResult result = tool().execute(Map.of(), context());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("count", 0);
    }
}
