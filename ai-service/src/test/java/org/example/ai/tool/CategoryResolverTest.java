package org.example.ai.tool;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.gateway.GatewayClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class CategoryResolverTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private CategoryResolver resolver() {
        return new CategoryResolver(new GatewayClient(wireMock.baseUrl(), 5));
    }

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("BUYER"), "ru");
    }

    @Test
    void resolve_knownSlug_returnsCategoryId() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/categories/cement"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"id\":42,\"slug\":\"cement\",\"nameRu\":\"Цемент\"}}")));

        Optional<Long> id = resolver().resolve("cement", context());

        assertThat(id).contains(42L);
    }

    @Test
    void resolve_unknownSlug_returnsEmpty_evenWithPlainTextErrorBody() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/categories/does-not-exist"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "text/plain").withBody("source cannot be null")));

        Optional<Long> id = resolver().resolve("does-not-exist", context());

        assertThat(id).isEmpty();
    }

    @Test
    void resolve_blankSlug_returnsEmptyWithoutCallingGateway() {
        Optional<Long> id = resolver().resolve("  ", context());

        assertThat(id).isEmpty();
        wireMock.verify(0, com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor(com.github.tomakehurst.wiremock.client.WireMock.anyUrl()));
    }
}
