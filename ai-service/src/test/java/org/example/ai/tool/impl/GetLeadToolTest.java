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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GetLeadToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private GetLeadTool tool() {
        return new GetLeadTool(new GatewayClient(wireMock.baseUrl(), 5));
    }

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("BUYER"), "ru");
    }

    @Test
    void execute_found_projectsItemsAndContact() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/leads/7"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"id":7,"status":"CONTACTED","contactName":"Ali","contactPhone":"+998900000000",
                          "items":[{"productId":5,"productNameSnapshot":"Cement","priceSnapshot":15000.0,"quantity":2}]}}
                        """)));

        ToolResult result = tool().execute(Map.of("id", 7), context());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("id", 7L).containsEntry("status", "CONTACTED").containsEntry("contactName", "Ali");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.data().get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsEntry("name", "Cement").containsEntry("quantity", 2);
    }

    @Test
    void execute_foreignOrMissingLead_returnsNotFoundResult() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/leads/99"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":false,\"message\":\"lead.forbidden\"}")));

        ToolResult result = tool().execute(Map.of("id", 99), context());

        assertThat(result.success()).isFalse();
    }

    @Test
    void execute_missingId_returnsErrorWithoutCallingGateway() {
        ToolResult result = tool().execute(Map.of(), context());

        assertThat(result.success()).isFalse();
    }
}
