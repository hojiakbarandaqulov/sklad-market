package org.example.ai.gateway;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private GatewayClient client() {
        return new GatewayClient(wireMock.baseUrl(), 5);
    }

    @Test
    void get_success_deserializesEnvelopeAndForwardsAuthAndLanguageHeaders() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/companies/acme"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"name\":\"Acme\"},\"message\":null}")));

        GatewayEnvelope<TestData> result = client().get(
                "/api/v1/companies/{slug}", null, "user-jwt-123", "RU",
                new ParameterizedTypeReference<GatewayEnvelope<TestData>>() {
                }, "acme");

        assertThat(result.success()).isTrue();
        assertThat(result.data().name()).isEqualTo("Acme");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/companies/acme"))
                .withHeader("Authorization", equalTo("Bearer user-jwt-123"))
                .withHeader("Accept-Language", equalTo("RU")));
    }

    @Test
    void get_withQueryParams_areSentOnTheWire() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/catalog"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":null}")));
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("q", "cement");
        params.add("page", "1");

        client().get("/api/v1/catalog", params, "token", "UZ",
                new ParameterizedTypeReference<GatewayEnvelope<Object>>() {
                });

        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/catalog"))
                .withQueryParam("q", equalTo("cement"))
                .withQueryParam("page", equalTo("1")));
    }

    @Test
    void get_400WithJsonBody_throwsGatewayNotFoundWithoutParsingBody() {
        wireMock.stubFor(get(urlPathMatching(".*")).willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":false,\"message\":\"Mahsulot topilmadi\",\"errors\":{},\"trace_id\":\"x\"}")));

        assertThatThrownBy(() -> client().get("/api/v1/products/{slug}", null, "t", "RU",
                new ParameterizedTypeReference<GatewayEnvelope<TestData>>() {
                }, "missing"))
                .isInstanceOf(GatewayNotFoundException.class);
    }

    @Test
    void get_400WithPlainTextBody_throwsGatewayNotFound() {
        wireMock.stubFor(get(urlPathMatching(".*"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "text/plain").withBody("source cannot be null")));

        assertThatThrownBy(() -> client().get("/api/v1/categories/{slug}", null, "t", "RU",
                new ParameterizedTypeReference<GatewayEnvelope<TestData>>() {
                }, "missing"))
                .isInstanceOf(GatewayNotFoundException.class);
    }

    @Test
    void get_500_throwsGatewayUnavailable() {
        wireMock.stubFor(get(urlPathMatching(".*")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client().get("/api/v1/catalog", null, "t", "RU",
                new ParameterizedTypeReference<GatewayEnvelope<TestData>>() {
                }))
                .isInstanceOf(GatewayUnavailableException.class);
    }

    @Test
    void get_connectionRefused_throwsGatewayUnavailable() {
        GatewayClient client = new GatewayClient("http://127.0.0.1:1", 1);

        assertThatThrownBy(() -> client.get("/api/v1/catalog", null, "t", "RU",
                new ParameterizedTypeReference<GatewayEnvelope<TestData>>() {
                }))
                .isInstanceOf(GatewayUnavailableException.class);
    }

    private record TestData(String name) {
    }
}
