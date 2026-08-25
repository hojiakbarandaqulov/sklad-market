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

class GetCartToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private GetCartTool tool() {
        return new GetCartTool(new GatewayClient(wireMock.baseUrl(), 5));
    }

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("BUYER"), "ru");
    }

    @Test
    void execute_projectsItemsAndTotals() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/cart"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"itemCount":2,"totalQuantity":5,"items":[
                          {"id":1,"productName":"Cement","productSlug":"cement-m500","price":15000.0,"currency":"UZS","companyName":"Acme","quantity":3},
                          {"id":2,"productName":"Brick","productSlug":"brick-red","price":500.0,"currency":"UZS","companyName":"Acme","quantity":2}
                        ]}}
                        """)));

        ToolResult result = tool().execute(Map.of(), context());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("itemCount", 2L).containsEntry("totalQuantity", 5L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.data().get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0)).containsEntry("productName", "Cement").containsEntry("quantity", 3);
    }

    @Test
    void execute_emptyCart_returnsZeroedProjection() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/cart"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"itemCount\":0,\"totalQuantity\":0,\"items\":[]}}")));

        ToolResult result = tool().execute(Map.of(), context());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("itemCount", 0L);
    }
}
