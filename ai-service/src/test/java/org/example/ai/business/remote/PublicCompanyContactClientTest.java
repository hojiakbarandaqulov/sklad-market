package org.example.ai.business.remote;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicCompanyContactClientTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    @Test
    void mapsKnownUnknownSlugStatusesToNotFound() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/companies/missing"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> new PublicCompanyContactClient(wireMock.baseUrl(), 1000)
                .fetch("missing", "EN"))
                .isInstanceOf(GatewayNotFoundException.class);
    }

    @Test
    void mapsRateLimitAndAuthorizationStatusesToTemporaryUnavailable() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/companies/limited"))
                .willReturn(aResponse().withStatus(429)));

        assertThatThrownBy(() -> new PublicCompanyContactClient(wireMock.baseUrl(), 1000)
                .fetch("limited", "EN"))
                .isInstanceOf(GatewayUnavailableException.class);
    }
}
