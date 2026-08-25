package org.example.ai.tool.impl;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.embedding.ProductEmbeddingRepository;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetCatalogFiltersToolTest {

    private final ProductEmbeddingRepository embeddingRepository = mock(ProductEmbeddingRepository.class);

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("BUYER"), "ru");
    }

    private GetCatalogFiltersTool tool() {
        GatewayClient client = new GatewayClient(wireMock.baseUrl(), 5);
        return new GetCatalogFiltersTool(client, new CategoryResolver(client), embeddingRepository);
    }

    @Test
    void execute_noCategory_capsAttributesAndDerivesRegionsFromIndex() {
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            values.append("\"v").append(i).append("\",");
        }
        String manyValues = values.substring(0, values.length() - 1);
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/catalog/filters"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"minPrice":100.0,"maxPrice":99999.0,
                          "attributes":{"color":[%s]}}}
                        """.formatted(manyValues))));
        when(embeddingRepository.findDistinctRegionIds(null, 20)).thenReturn(java.util.stream.LongStream.rangeClosed(1, 25).boxed().toList());

        ToolResult result = tool().execute(Map.of(), context());

        assertThat(result.data()).doesNotContainKeys("minPrice", "maxPrice")
                .containsEntry("priceRangeAvailable", false)
                .containsEntry("priceRangeReason", "currency_not_scoped");
        @SuppressWarnings("unchecked")
        List<Long> regionIds = (List<Long>) result.data().get("regionIds");
        assertThat(regionIds).hasSize(20);
        assertThat(result.data()).containsEntry("regionIdsAvailable", true)
                .containsEntry("regionIdsSource", "ai_product_index");
        @SuppressWarnings("unchecked")
        Map<String, List<String>> attributes = (Map<String, List<String>>) result.data().get("attributes");
        assertThat(attributes.get("color")).hasSize(8);
    }

    @Test
    void execute_withCategorySlug_resolvesAndForwardsNumericCategoryParam() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/categories/cement"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"id\":7}}")));
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/catalog/filters"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"minPrice\":0.0,\"maxPrice\":0.0,\"attributes\":{}}}")));
        when(embeddingRepository.findDistinctRegionIds(7L, 20)).thenReturn(List.of(2L, 9L));

        tool().execute(Map.of("categorySlug", "cement"), context());

        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/catalog/filters")).withQueryParam("category", equalTo("7")));
        org.mockito.Mockito.verify(embeddingRepository).findDistinctRegionIds(7L, 20);
    }

    @Test
    void execute_indexUnavailable_marksRegionsUnavailableInsteadOfClaimingNoneExist() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/catalog/filters"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"minPrice\":0.0,\"maxPrice\":0.0,\"attributes\":{}}}")));
        when(embeddingRepository.findDistinctRegionIds(null, 20)).thenThrow(new IllegalStateException("index down"));

        ToolResult result = tool().execute(Map.of(), context());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("regionIds", List.of())
                .containsEntry("regionIdsAvailable", false)
                .containsEntry("regionIdsSource", "unavailable");
    }

    @Test
    void execute_unknownCategorySlug_returnsNotFound() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/categories/unknown")).willReturn(aResponse().withStatus(400)));

        ToolResult result = tool().execute(Map.of("categorySlug", "unknown"), context());

        assertThat(result.success()).isFalse();
    }
}
