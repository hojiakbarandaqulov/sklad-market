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

class ListCategoriesToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private ListCategoriesTool tool() {
        return new ListCategoriesTool(new GatewayClient(wireMock.baseUrl(), 5));
    }

    /**
     * PLAN.md §7 item 9 (re-verified live for Phase 2): the list endpoint does NOT filter
     * {@code isActive} server-side — the tool must filter inactive categories out itself.
     */
    @Test
    void execute_filtersOutInactiveCategories_andUsesLocaleMatchingDisplayName() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/categories"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"content":[
                          {"id":1,"nameRu":"Цемент","slug":"cement","sortOrder":1,"isActive":true},
                          {"id":2,"nameRu":"Устарело","slug":"old","sortOrder":2,"isActive":false}
                        ]}}
                        """)));

        ToolResult result = tool().execute(Map.of(), new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("BUYER"), "ru"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) result.data().get("categories");
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0)).containsEntry("name", "Цемент").containsEntry("slug", "cement");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/categories")).withHeader("Accept-Language", equalTo("RU")));
    }

    @Test
    void execute_sendsZeroBasedPageParam() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/categories"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"content\":[]}}")));

        tool().execute(Map.of(), new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("BUYER"), "uz"));

        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/categories")).withQueryParam("page", equalTo("0")));
    }
}
