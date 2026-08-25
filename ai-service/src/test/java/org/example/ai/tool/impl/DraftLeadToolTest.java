package org.example.ai.tool.impl;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.example.ai.gateway.GatewayClient;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.example.entity.ActionDraft;
import org.example.service.ActionDraftService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DraftLeadToolTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    @Mock
    private ActionDraftService actionDraftService;

    private DraftLeadTool tool() {
        return new DraftLeadTool(new GatewayClient(wireMock.baseUrl(), 5), actionDraftService);
    }

    private static ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub-buyer", "user-jwt", Set.of("BUYER"), "ru");
    }

    private static Map<String, Object> baseArgs(Object slugs) {
        return Map.of(
                "productSlugs", slugs,
                "quantity", 3,
                "contactName", "Ali Valiyev",
                "contactPhone", "+998901234567");
    }

    private void stubProduct(String slug, long id, long companyId, String status) {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/products/slug/" + slug))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"success":true,"data":{"id":%d,"name":"%s","slug":"%s","price":15000.0,"currency":"UZS",
                          "status":"%s","company":{"id":%d,"name":"Acme","slug":"acme"}}}
                        """.formatted(id, slug, slug, status, companyId))));
    }

    private ActionDraft fakeSavedDraft() {
        ActionDraft draft = new ActionDraft();
        draft.setId(UUID.randomUUID());
        draft.setExpiresAt(Instant.now().plusSeconds(1800));
        return draft;
    }

    @Test
    void execute_singleProduct_persistsDraftWithSourceProductAndReturnsDraftMetadata() {
        stubProduct("cement-m500", 42, 9, "APPROVED");
        when(actionDraftService.create(any(), eq("sub-buyer"), eq("LEAD"), any())).thenReturn(fakeSavedDraft());

        ToolResult result = tool().execute(baseArgs(List.of("cement-m500")), context());

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("draftType", "LEAD").containsEntry("status", "DRAFT");
        assertThat(result.data().get("draftId")).isNotNull();

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionDraftService).create(any(), eq("sub-buyer"), eq("LEAD"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).containsEntry("source", "PRODUCT").containsEntry("productId", 42L).containsEntry("quantity", 3);
        assertThat(payload).doesNotContainKey("productIds");
        assertThat(payload.get("contactName")).isEqualTo("Ali Valiyev");
    }

    @Test
    void execute_multipleProductsSameCompany_persistsDraftWithSourceCart() {
        stubProduct("cement-m500", 42, 9, "APPROVED");
        stubProduct("brick-red", 43, 9, "APPROVED");
        when(actionDraftService.create(any(), any(), any(), any())).thenReturn(fakeSavedDraft());

        ToolResult result = tool().execute(baseArgs(List.of("cement-m500", "brick-red")), context());

        assertThat(result.success()).isTrue();
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(actionDraftService).create(any(), any(), any(), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).containsEntry("source", "CART");
        @SuppressWarnings("unchecked")
        List<Long> productIds = (List<Long>) payload.get("productIds");
        assertThat(productIds).containsExactly(42L, 43L);
        assertThat(payload).doesNotContainKey("productId");
    }

    @Test
    void execute_multipleProductsDifferentCompanies_rejectsWithoutCreatingDraft() {
        stubProduct("cement-m500", 42, 9, "APPROVED");
        stubProduct("pipe-x", 44, 10, "APPROVED");

        ToolResult result = tool().execute(baseArgs(List.of("cement-m500", "pipe-x")), context());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).containsIgnoringCase("different seller");
        verify(actionDraftService, never()).create(any(), any(), any(), any());
    }

    @Test
    void execute_unapprovedProduct_rejectsWithoutCreatingDraft() {
        stubProduct("pending-product", 50, 9, "PENDING");

        ToolResult result = tool().execute(baseArgs(List.of("pending-product")), context());

        assertThat(result.success()).isFalse();
        verify(actionDraftService, never()).create(any(), any(), any(), any());
    }

    @Test
    void execute_productNotFound_rejectsWithoutCreatingDraft() {
        wireMock.stubFor(get(urlPathEqualTo("/api/v1/products/slug/missing"))
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json")
                        .withBody("{\"success\":false,\"message\":\"not found\"}")));

        ToolResult result = tool().execute(baseArgs(List.of("missing")), context());

        assertThat(result.success()).isFalse();
        verify(actionDraftService, never()).create(any(), any(), any(), any());
    }

    @Test
    void execute_missingContactFields_rejectsBeforeCallingGateway() {
        ToolResult result = tool().execute(Map.of("productSlugs", List.of("cement-m500")), context());

        assertThat(result.success()).isFalse();
        wireMock.verify(0, com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor(com.github.tomakehurst.wiremock.client.WireMock.anyUrl()));
    }

    @Test
    void execute_emptyProductSlugs_rejectsWithClearError() {
        ToolResult result = tool().execute(baseArgs(List.of()), context());

        assertThat(result.success()).isFalse();
        verify(actionDraftService, never()).create(any(), any(), any(), any());
    }

    @Test
    void execute_zeroQuantity_rejectsWithClearError() {
        ToolResult result = tool().execute(Map.of(
                "productSlugs", List.of("cement-m500"),
                "quantity", 0,
                "contactName", "Ali",
                "contactPhone", "+998901234567"), context());

        assertThat(result.success()).isFalse();
        verify(actionDraftService, never()).create(any(), any(), any(), any());
    }

    @Test
    void execute_invalidNeededDate_rejectsWithClearError() {
        ToolResult result = tool().execute(Map.of(
                "productSlugs", List.of("cement-m500"),
                "contactName", "Ali",
                "contactPhone", "+998901234567",
                "neededDate", "not-a-date"), context());

        assertThat(result.success()).isFalse();
        verify(actionDraftService, never()).create(any(), any(), any(), any());
    }
}
