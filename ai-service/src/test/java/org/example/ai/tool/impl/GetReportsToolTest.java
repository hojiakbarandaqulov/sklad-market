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

/** Verifies {@code get_reports} surfaces the platform's REAL {@code ReasonCode} values verbatim (never invented). */
class GetReportsToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private GetReportsTool tool() {
        return new GetReportsTool(new GatewayClient(wireMock.baseUrl(), 5));
    }

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("ADMIN"), "ru");
    }

    @Test
    void execute_projectsRealReasonCodeVerbatim() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/admin/reports"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"content":[
                          {"id":1,"status":"NEW","targetType":"PRODUCT","targetId":42,"reasonCode":"FAKE","createdDate":"2026-07-15T12:00:00"}
                        ]}}
                        """)));

        ToolResult result = tool().execute(Map.of(), context());

        assertThat(result.success()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reports = (List<Map<String, Object>>) result.data().get("reports");
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0)).containsEntry("reasonCode", "FAKE").containsEntry("targetType", "PRODUCT").containsEntry("targetId", 42L);
    }

    @Test
    void execute_withFilters_forwardsStatusAndTargetTypeParams() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/admin/reports"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":{\"content\":[]}}")));

        tool().execute(Map.of("status", "NEW", "targetType", "COMPANY"), context());

        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/v1/admin/reports"))
                .withQueryParam("status", equalTo("NEW"))
                .withQueryParam("targetType", equalTo("COMPANY")));
    }

    @Test
    void allowedRoles_isAdminAndSuperAdminOnly() {
        assertThat(tool().allowedRoles()).containsExactlyInAnyOrder("ADMIN", "SUPER_ADMIN");
    }
}
