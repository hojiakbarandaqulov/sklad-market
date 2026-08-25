package org.example.ai.tool.impl;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.gateway.GatewayClient;
import org.example.ai.tool.CategoryResolver;
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

class SearchProductsToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "user-jwt", Set.of("BUYER"), "ru");
    }

    private SearchProductsTool tool() {
        GatewayClient client = new GatewayClient(wireMock.baseUrl(), 5);
        return new SearchProductsTool(client, new CategoryResolver(client));
    }

    @Test
    void execute_noCategory_projectsCompactFieldsAndForwardsUserJwt() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/catalog"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"items":[
                          {"id":1,"name":"Cement M500","slug":"cement-m500","price":15000.0,"currency":"UZS","regionId":1,"shortDescription":"Bulk cement","categoryId":3,
                           "images":[{"id":"img-1","url":"/files/full.jpg","thumbnail_urls":{"medium":"/files/card.jpg"},"is_primary":true}]}
                        ],"meta":{"total":1,"page":1,"perPage":10,"totalPages":1}}}
                        """)));

        ToolResult result = tool().execute(Map.of("query", "cement"), context());

        assertThat(result.success()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.data().get("items");
        assertThat(items).hasSize(1);
        assertThat(result.data()).containsEntry("kind", "business_search").containsEntry("presentation", "CARDS");
        assertThat(items.get(0)).containsEntry("type", "PRODUCT")
                .containsEntry("name", "Cement M500")
                .containsEntry("slug", "cement-m500")
                .containsEntry("imageUrl", "/files/card.jpg");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/catalog"))
                .withHeader("Authorization", equalTo("Bearer user-jwt"))
                .withQueryParam("q", equalTo("cement")));
    }

    @Test
    void schemaDoesNotAdvertiseUnsupportedPriceFilters() {
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) tool().parametersSchema().get("properties");

        assertThat(properties).containsKeys("query", "categorySlug", "page")
                .doesNotContainKeys("minPrice", "maxPrice", "saleType");
    }

    @Test
    void execute_categorySlug_resolvesToNumericIdBeforeCallingCatalog() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/categories/cement"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"id\":7,\"slug\":\"cement\"}}")));
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/catalog"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"items\":[],\"meta\":{\"total\":0,\"page\":1,\"perPage\":10,\"totalPages\":0}}}")));

        tool().execute(Map.of("categorySlug", "cement"), context());

        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/catalog")).withQueryParam("category", equalTo("7")));
    }

    @Test
    void execute_unknownCategorySlug_returnsNotFoundWithoutCallingCatalog() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/categories/does-not-exist"))
                .willReturn(aResponse().withStatus(400)));

        ToolResult result = tool().execute(Map.of("categorySlug", "does-not-exist"), context());

        assertThat(result.success()).isFalse();
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/catalog")));
    }
}
