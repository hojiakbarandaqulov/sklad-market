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

class GetMyLeadsToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private GetMyLeadsTool tool() {
        return new GetMyLeadsTool(new GatewayClient(wireMock.baseUrl(), 5));
    }

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("BUYER"), "ru");
    }

    @Test
    void execute_projectsLeadsAndForwardsCamelCasePerPageParam() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/leads"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"items":[
                          {"id":1,"status":"NEW","items":[{"productId":5,"productNameSnapshot":"Cement","quantity":2}]}
                        ],"meta":{"total":1,"page":1,"perPage":10,"totalPages":1}}}
                        """)));

        ToolResult result = tool().execute(Map.of(), context());

        assertThat(result.success()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> leads = (List<Map<String, Object>>) result.data().get("leads");
        assertThat(leads).hasSize(1);
        assertThat(leads.get(0)).containsEntry("id", 1L).containsEntry("status", "NEW").containsEntry("firstItemName", "Cement");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/leads")).withQueryParam("perPage", equalTo("10")));
    }

    @Test
    void execute_withStatusFilter_forwardsStatusQueryParam() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/leads"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"items\":[],\"meta\":{\"total\":0,\"page\":1,\"perPage\":10,\"totalPages\":0}}}")));

        tool().execute(Map.of("status", "CLOSED"), context());

        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/leads")).withQueryParam("status", equalTo("CLOSED")));
    }
}
