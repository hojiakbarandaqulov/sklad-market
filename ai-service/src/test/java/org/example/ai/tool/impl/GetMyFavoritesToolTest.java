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

class GetMyFavoritesToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private GetMyFavoritesTool tool() {
        return new GetMyFavoritesTool(new GatewayClient(wireMock.baseUrl(), 5));
    }

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("BUYER"), "ru");
    }

    @Test
    void execute_projectsFavoritesFromSpringPageContent() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/product-favorites"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"content":[
                          {"name":"Cement M500","slug":"cement-m500","price":15000.0,"currency":"UZS","shortDescription":"Bulk"}
                        ],"totalElements":1,"number":0}}
                        """)));

        ToolResult result = tool().execute(Map.of(), context());

        assertThat(result.success()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> favorites = (List<Map<String, Object>>) result.data().get("favorites");
        assertThat(favorites).hasSize(1);
        assertThat(favorites.get(0)).containsEntry("name", "Cement M500").containsEntry("slug", "cement-m500");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/product-favorites")).withQueryParam("perPage", equalTo("10")));
    }

    @Test
    void execute_noFavorites_returnsEmptyList() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/product-favorites"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"content\":[]}}")));

        ToolResult result = tool().execute(Map.of(), context());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("count", 0);
    }
}
