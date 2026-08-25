package org.example.ai.tool.impl;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.gateway.GatewayClient;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GetProductToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "user-jwt", Set.of("BUYER"), "ru");
    }

    private GetProductTool tool() {
        return new GetProductTool(new GatewayClient(wireMock.baseUrl(), 5));
    }

    @Test
    void execute_found_projectsCompanyAndCategoryFromSnakeCaseWireFields() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/products/slug/cement-m500"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{
                          "name":"Cement M500","slug":"cement-m500","description":"Full desc",
                          "short_description":"Short","price":15000.0,"currency":"UZS","status":"APPROVED",
                          "region_id":1,"district_id":2,
                          "images":[{"url":"/files/full.jpg","thumbnail_urls":{"small":"/files/card.jpg"},"is_primary":true}],
                          "company":{"id":9,"name":"Acme","slug":"acme"},
                          "category":{"id":3,"name":"Construction","slug":"construction"}
                        }}
                        """)));

        ToolResult result = tool().execute(Map.of("slug", "cement-m500"), context());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("kind", "business_search")
                .containsEntry("presentation", "CARDS")
                .containsEntry("name", "Cement M500")
                .containsEntry("status", "APPROVED")
                .containsEntry("imageUrl", "/files/card.jpg");
        @SuppressWarnings("unchecked")
        Map<String, Object> company = (Map<String, Object>) result.data().get("company");
        assertThat(company).containsEntry("slug", "acme");
    }

    @Test
    void execute_notFound_returnsNotFoundResultOn400() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/products/slug/missing"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":false,\"message\":\"Mahsulot topilmadi\",\"errors\":{},\"trace_id\":\"x\"}")));

        ToolResult result = tool().execute(Map.of("slug", "missing"), context());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("missing");
    }
}
