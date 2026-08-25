package org.example.ai.matching.tool;

import org.example.ai.matching.dto.BuyerOpportunity;
import org.example.ai.matching.dto.BuyerOpportunityResult;
import org.example.ai.matching.service.BuyerOpportunityService;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendBuyersToolTest {

    @Test
    void isSellerOnly_andDefenseInDepthRejectsDirectBuyerExecution() {
        BuyerOpportunityService service = mock(BuyerOpportunityService.class);
        RecommendBuyersTool tool = new RecommendBuyersTool(service);

        ToolResult result = tool.execute(Map.of(), context("BUYER"));

        assertThat(tool.allowedRoles()).containsExactly("SELLER");
        assertThat(result.success()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(403);
        verify(service, never()).recommend(any(), any(), any(), any(), any());
    }

    @Test
    void sellerResult_containsNoContactOrAutomaticOutreach() {
        BuyerOpportunityService service = mock(BuyerOpportunityService.class);
        BuyerOpportunity opportunity = new BuyerOpportunity(
                9L, "NEW", "2026-08-20", 80, List.of("NEW_REQUEST"),
                List.of(new BuyerOpportunity.RequestedItem(4L, "Cement", 20)),
                "VIEW_AUTHORIZED_LEAD", false);
        when(service.recommend(any(), any(), any(), any(), any())).thenReturn(new BuyerOpportunityResult(
                List.of(opportunity), 100, 325, true, Instant.parse("2026-08-11T10:00:00Z"),
                "CALLER_SCOPED_SELLER_LEADS", "contacts excluded", false));
        RecommendBuyersTool tool = new RecommendBuyersTool(service);

        ToolResult result = tool.execute(Map.of("query", "cement"), context("SELLER"));

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsEntry("kind", "buyer_recommendations");
        assertThat(result.data()).containsEntry("evaluatedLeadCount", 100)
                .containsEntry("totalLeadCount", 325L)
                .containsEntry("candidatesTruncated", true)
                .containsEntry("asOf", Instant.parse("2026-08-11T10:00:00Z"));
        assertThat((List<?>) result.data().get("items")).hasSize(1);
        assertThat(result.data().toString()).contains("leadId=9", "automaticOutreachAllowed=false");
        assertThat(result.data().toString()).doesNotContain("buyerId", "contactPhone", "contactEmail", "comment");
    }

    private ToolExecutionContext context(String role) {
        return new ToolExecutionContext(UUID.randomUUID(), "sub", "jwt", Set.of(role), "en");
    }
}
