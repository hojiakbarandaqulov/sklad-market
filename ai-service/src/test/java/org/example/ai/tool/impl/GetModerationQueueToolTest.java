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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class GetModerationQueueToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private GetModerationQueueTool tool() {
        return new GetModerationQueueTool(new GatewayClient(wireMock.baseUrl(), 5));
    }

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-1", "jwt", Set.of("ADMIN"), "ru");
    }

    @Test
    void execute_noTargetType_fetchesBothProductsAndCompanies() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/admin/products/moderation-queue"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":[{"id":1,"name":"Cement M500","companyId":9,"categoryId":3,"createdAt":"2026-07-10T10:00:00"}]}
                        """)));
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/admin/companies/moderation-queue"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":[{"id":9,"name":"Acme LLC","slug":"acme","verificationStatus":"PENDING_VERIFICATION","createdAt":"2026-07-09T09:00:00"}]}
                        """)));

        ToolResult result = tool().execute(Map.of(), context());

        assertThat(result.success()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) result.data().get("pendingProducts");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> companies = (List<Map<String, Object>>) result.data().get("pendingCompanies");
        assertThat(products).hasSize(1);
        assertThat(products.get(0)).containsEntry("name", "Cement M500").containsEntry("targetType", "PRODUCT");
        assertThat(companies).hasSize(1);
        assertThat(companies.get(0)).containsEntry("name", "Acme LLC").containsEntry("targetType", "COMPANY");
    }

    @Test
    void execute_targetTypeProduct_onlyCallsProductQueue() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/admin/products/moderation-queue"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":true,\"data\":[]}")));

        ToolResult result = tool().execute(Map.of("targetType", "PRODUCT"), context());

        assertThat(result.success()).isTrue();
        assertThat((List<?>) result.data().get("pendingCompanies")).isEmpty();
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/api/v1/admin/companies/moderation-queue")));
    }

    @Test
    void allowedRoles_isAdminAndSuperAdminOnly() {
        assertThat(tool().allowedRoles()).containsExactlyInAnyOrder("ADMIN", "SUPER_ADMIN");
    }
}
