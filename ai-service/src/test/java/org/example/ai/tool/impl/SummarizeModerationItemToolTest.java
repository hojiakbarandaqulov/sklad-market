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

class SummarizeModerationItemToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private SummarizeModerationItemTool tool() {
        return new SummarizeModerationItemTool(new GatewayClient(wireMock.baseUrl(), 5));
    }

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("ADMIN"), "ru");
    }

    @Test
    void execute_product_usesRealDetailEndpoint() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/admin/products/42"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"id":42,"name":"Cement M500","status":"PENDING","companyId":9,"categoryId":3,
                          "shortDescription":"Bulk cement","description":"High quality cement in bags",
                          "attributes":{"grade":"M500"},"rejectReason":null,"createdAt":"2026-07-10T10:00:00"}}
                        """)));

        ToolResult result = tool().execute(Map.of("targetType", "PRODUCT", "id", 42), context());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("name", "Cement M500").containsEntry("status", "PENDING");
    }

    @Test
    void execute_company_notInModerationQueue_returnsHonestGapExplanation_notInventedDetail() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/admin/companies/moderation-queue"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":[]}")));

        ToolResult result = tool().execute(Map.of("targetType", "COMPANY", "id", 99), context());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("no per-id admin detail endpoint");
    }

    @Test
    void execute_company_foundInQueue_projectsRealFields() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/admin/companies/moderation-queue"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":[{"id":9,"name":"Acme LLC","slug":"acme","verificationStatus":"PENDING_VERIFICATION",
                          "shortDescription":"Wholesale supplier","address":"Tashkent","createdAt":"2026-07-09T09:00:00"}]}
                        """)));

        ToolResult result = tool().execute(Map.of("targetType", "COMPANY", "id", 9), context());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("name", "Acme LLC").containsEntry("verificationStatus", "PENDING_VERIFICATION");
    }

    @Test
    void execute_invalidTargetType_returnsError() {
        ToolResult result = tool().execute(Map.of("targetType", "CHAT", "id", 1), context());
        assertThat(result.success()).isFalse();
    }

    @Test
    void allowedRoles_isAdminAndSuperAdminOnly() {
        assertThat(tool().allowedRoles()).containsExactlyInAnyOrder("ADMIN", "SUPER_ADMIN");
    }
}
