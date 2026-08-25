package org.example.ai.intent.tool;

import org.example.ai.intent.dto.BuyingIntentResponse;
import org.example.ai.intent.dto.BuyingIntentMatchResponse;
import org.example.ai.intent.dto.BuyingIntentMatchResult;
import org.example.ai.intent.service.BuyingIntentService;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.example.ai.tool.ToolRegistry;
import org.example.dto.PageMeta;
import org.example.dto.PagedResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuyingIntentToolRoleAndConfirmationTest {

    @Test
    void registrySeparatesBuyerManagementFromSellerDiscovery() {
        BuyingIntentService service = mock(BuyingIntentService.class);
        List<AgentTool> tools = List.of(
                new DraftBuyingIntentTool(service),
                new GetMyBuyingIntentsTool(service),
                new CloseBuyingIntentTool(),
                new SearchBuyingIntentsTool(service));
        ToolRegistry registry = new ToolRegistry(tools);

        assertThat(registry.availableFor(Set.of("BUYER")).stream().map(AgentTool::name))
                .containsExactlyInAnyOrder("draft_buying_intent", "get_my_buying_intents", "close_buying_intent");
        assertThat(registry.availableFor(Set.of("SELLER")).stream().map(AgentTool::name))
                .containsExactly("search_buying_intents");
    }

    @Test
    void closeToolOnlyPreparesUiConfirmationAndNeverMutatesEvenIfModelSuppliesTrue() {
        BuyingIntentService service = mock(BuyingIntentService.class);
        CloseBuyingIntentTool tool = new CloseBuyingIntentTool();
        UUID intentId = UUID.randomUUID();

        ToolResult result = tool.execute(
                Map.of("intentId", intentId.toString(), "confirm", true), context("BUYER"));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("confirmationRequired", true)
                .containsEntry("closed", false)
                .containsEntry("closeEndpoint", "/api/v1/ai/buying-intents/" + intentId + "/close");
        verify(service, never()).close(any(), any());
        Map<?, ?> item = (Map<?, ?>) ((List<?>) result.data().get("items")).get(0);
        assertThat(item.get("status")).isEqualTo("CONFIRMATION_REQUIRED");
    }

    @Test
    void draftRemainsUnpublishedAndReturnsExplicitConfirmEndpoint() {
        BuyingIntentService service = mock(BuyingIntentService.class);
        UUID id = UUID.randomUUID();
        when(service.createDraft(any(), any())).thenReturn(response(id, "DRAFT"));
        DraftBuyingIntentTool tool = new DraftBuyingIntentTool(service);

        ToolResult result = tool.execute(
                Map.of("category", "Cement", "needText", "Need M500 cement"), context("BUYER"));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("kind", "buying_intent_draft")
                .containsEntry("status", "DRAFT")
                .containsEntry("requiresPublicationConfirmation", true)
                .containsEntry("confirmEndpoint", "/api/v1/ai/buying-intents/" + id + "/publish");
        assertThat(result.data().get("confirmBody")).isEqualTo(Map.of("publicationConsent", true));
        assertThat(result.data().get("publicationDisclosure").toString()).contains("seller");
        assertThat((List<?>) result.data().get("items")).hasSize(1);
        Map<?, ?> item = (Map<?, ?>) ((List<?>) result.data().get("items")).get(0);
        assertThat(item.get("quantity")).isEqualTo(new BigDecimal("10"));
        assertThat(item.get("quantityUnit")).isEqualTo("TON");
        assertThat(item.get("budgetMin")).isEqualTo(new BigDecimal("1000"));
        assertThat(item.get("budgetMax")).isEqualTo(new BigDecimal("2000"));
        assertThat(item.get("currency")).isEqualTo("UZS");
        assertThat(item.get("publicationDisclosure").toString()).contains("seller");
    }

    @Test
    void buyerCannotDirectlyExecuteSellerIntentSearch() {
        BuyingIntentService service = mock(BuyingIntentService.class);
        SearchBuyingIntentsTool tool = new SearchBuyingIntentsTool(service);

        ToolResult result = tool.execute(Map.of(), context("BUYER"));

        assertThat(result.success()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(403);
        verify(service, never()).searchPublished(any(), any(), any(), any());
    }

    @Test
    void successfulListAndSearchExposeStructuredResultSetContract() {
        BuyingIntentService service = mock(BuyingIntentService.class);
        UUID id = UUID.randomUUID();
        when(service.listOwn("buyer-sub", 1, 20, null)).thenReturn(new PagedResponse<>(
                List.of(response(id, "PUBLISHED")), new PageMeta(1, 1, 20, 1)));
        when(service.searchPublished(any(), any(), any(), any())).thenReturn(new BuyingIntentMatchResult(
                List.of(new BuyingIntentMatchResponse(
                        id, "Cement", "Tashkent", "Need cement", null, null, null, null,
                        "UZS", Instant.parse("2026-08-12T10:00:00Z"), 80,
                        List.of("CATEGORY_MATCH"), false, "NOT_COLLECTED", false)),
                1, 125, true, Instant.parse("2026-08-11T10:00:00Z"),
                "Owner/contact columns excluded; text is seller-visible.", false));

        ToolResult mine = new GetMyBuyingIntentsTool(service).execute(Map.of(), context("BUYER"));
        ToolResult search = new SearchBuyingIntentsTool(service).execute(Map.of(), context("SELLER"));

        assertThat(mine.data()).containsEntry("kind", "buying_intents");
        assertThat((List<?>) mine.data().get("items")).hasSize(1);
        assertThat(search.data()).containsEntry("kind", "buying_intent_matches");
        assertThat((List<?>) search.data().get("items")).hasSize(1);
        assertThat(search.data()).containsEntry("evaluatedIntentCount", 1)
                .containsEntry("totalIntentCount", 125L)
                .containsEntry("candidatesTruncated", true)
                .containsEntry("sellerVisibleUserText", true);
    }

    private ToolExecutionContext context(String role) {
        return new ToolExecutionContext(UUID.randomUUID(), "buyer-sub", "jwt", Set.of(role), "en");
    }

    private BuyingIntentResponse response(UUID id, String status) {
        Instant now = Instant.parse("2026-08-11T10:00:00Z");
        return new BuyingIntentResponse(
                id, status, "Cement", "Tashkent", "Need cement", new BigDecimal("10"), "TON",
                new BigDecimal("1000"), new BigDecimal("2000"), "UZS",
                now.plusSeconds(3600), null, null, null, now, now,
                false, "NOT_COLLECTED", "OWNER_ONLY",
                BuyingIntentService.PUBLICATION_DISCLOSURE, false);
    }
}
