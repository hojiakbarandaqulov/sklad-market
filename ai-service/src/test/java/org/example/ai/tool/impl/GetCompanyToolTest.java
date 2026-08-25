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

class GetCompanyToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("BUYER"), "ru");
    }

    private GetCompanyTool tool() {
        return new GetCompanyTool(new GatewayClient(wireMock.baseUrl(), 5));
    }

    @Test
    void execute_found_projectsOnlyAllowlistedPublicProfileFields() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/companies/acme"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"id":9,"name":"Acme","slug":"acme","status":"VERIFIED",
                          "regionId":1,"districtId":2,"address":"Tashkent","lat":"41.3","lng":"69.2",
                          "phonePrimary":"+998901234567","phoneSecondary":"+998909876543",
                          "website":"https://acme.example","companyCreatedDate":"2018-05-12",
                          "ownerUserId":777,"stir":"private-tax-id"}}
                        """)));

        ToolResult result = tool().execute(Map.of("slug", "acme"), context());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("kind", "business_search")
                .containsEntry("presentation", "CARDS")
                .containsEntry("name", "Acme")
                .containsEntry("status", "VERIFIED")
                .containsEntry("address", "Tashkent");
        assertThat(result.data())
                .containsEntry("phonePrimary", "+998901234567")
                .containsEntry("phoneSecondary", "+998909876543")
                .containsEntry("website", "https://acme.example")
                .containsEntry("companyCreatedDate", java.time.LocalDate.of(2018, 5, 12))
                .doesNotContainKeys("ownerUserId", "stir", "lat", "lng");
    }

    @Test
    void execute_notFound_plainTextErrorBody_returnsNotFoundResult() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/companies/missing"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "text/plain").withBody("companiya topilmadi")));

        ToolResult result = tool().execute(Map.of("slug", "missing"), context());

        assertThat(result.success()).isFalse();
    }
}
