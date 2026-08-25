package org.example.ai.tool;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.provider.ChatModelProvider;
import org.example.ai.tool.impl.DraftChatReplyTool;
import org.example.ai.tool.impl.DraftLeadReplyTool;
import org.example.ai.tool.impl.GetCompanyTool;
import org.example.ai.tool.impl.GetModerationQueueTool;
import org.example.ai.tool.impl.GetReportsTool;
import org.example.ai.tool.impl.GetSellerLeadsTool;
import org.example.ai.tool.impl.SummarizeModerationItemTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * PLAN.md Phase 6, the required "test matrix proving a BUYER token reaches none of it" — registry
 * layer. Constructs the REAL {@link ToolRegistry} with the REAL Phase 6 tool instances (not fakes)
 * so this test breaks if a tool's {@code allowedRoles()} is ever loosened by accident. The second
 * enforcement layer ({@code @PreAuthorize} on {@code AiSellerController}/{@code AiAdminController})
 * is proven separately in their respective {@code WebMvcTest}s — PLAN.md's "dual-layer" requirement.
 */
class SellerAdminToolRoleGatingTest {

    private static final List<String> SELLER_TOOL_NAMES =
            List.of("get_seller_leads", "draft_lead_reply", "draft_chat_reply");
    private static final List<String> ADMIN_TOOL_NAMES =
            List.of("get_moderation_queue", "get_reports", "summarize_moderation_item");

    private ToolRegistry realRegistry() {
        GatewayClient gatewayClient = mock(GatewayClient.class);
        ChatModelProvider chatModelProvider = mock(ChatModelProvider.class);
        List<AgentTool> tools = List.of(
                new GetCompanyTool(gatewayClient), // an open, role-unrestricted tool for contrast
                new GetSellerLeadsTool(gatewayClient),
                new DraftLeadReplyTool(gatewayClient, chatModelProvider, "gemini-2.5-flash"),
                new DraftChatReplyTool(gatewayClient, chatModelProvider, "gemini-2.5-flash"),
                new GetModerationQueueTool(gatewayClient),
                new GetReportsTool(gatewayClient),
                new SummarizeModerationItemTool(gatewayClient));
        return new ToolRegistry(tools);
    }

    @Test
    void buyerRole_reachesNoSellerOrAdminTool_butStillReachesOpenTools() {
        ToolRegistry registry = realRegistry();

        List<String> availableToBuyer = registry.availableFor(Set.of("BUYER")).stream().map(AgentTool::name).toList();

        assertThat(availableToBuyer).contains("get_company"); // sanity: open tools still work
        for (String sellerTool : SELLER_TOOL_NAMES) {
            assertThat(availableToBuyer).as("BUYER must not see %s", sellerTool).doesNotContain(sellerTool);
            assertThat(registry.findAllowed(sellerTool, Set.of("BUYER"))).as("findAllowed must deny %s to BUYER", sellerTool).isEmpty();
        }
        for (String adminTool : ADMIN_TOOL_NAMES) {
            assertThat(availableToBuyer).as("BUYER must not see %s", adminTool).doesNotContain(adminTool);
            assertThat(registry.findAllowed(adminTool, Set.of("BUYER"))).as("findAllowed must deny %s to BUYER", adminTool).isEmpty();
        }
    }

    @Test
    void sellerRole_reachesSellerToolsOnly_notAdminTools() {
        ToolRegistry registry = realRegistry();

        List<String> availableToSeller = registry.availableFor(Set.of("SELLER")).stream().map(AgentTool::name).toList();

        assertThat(availableToSeller).containsAll(SELLER_TOOL_NAMES);
        for (String adminTool : ADMIN_TOOL_NAMES) {
            assertThat(availableToSeller).doesNotContain(adminTool);
        }
    }

    @Test
    void adminRole_reachesAdminToolsOnly_notSellerTools() {
        ToolRegistry registry = realRegistry();

        List<String> availableToAdmin = registry.availableFor(Set.of("ADMIN")).stream().map(AgentTool::name).toList();

        assertThat(availableToAdmin).containsAll(ADMIN_TOOL_NAMES);
        for (String sellerTool : SELLER_TOOL_NAMES) {
            assertThat(availableToAdmin).doesNotContain(sellerTool);
        }
    }

    @Test
    void superAdminRole_alsoReachesAdminTools() {
        ToolRegistry registry = realRegistry();

        List<String> availableToSuperAdmin = registry.availableFor(Set.of("SUPER_ADMIN")).stream().map(AgentTool::name).toList();

        assertThat(availableToSuperAdmin).containsAll(ADMIN_TOOL_NAMES);
    }

    @Test
    void multiRoleCaller_getsUnionOfAllApplicableToolsets() {
        ToolRegistry registry = realRegistry();

        // Regression guard for the live-roles fix: a caller holding BOTH SELLER and ADMIN must see
        // both toolsets — this would fail under the old single-role Conversation.userRole snapshot,
        // which only ever stored one alphabetically-first role.
        List<String> available = registry.availableFor(Set.of("SELLER", "ADMIN")).stream().map(AgentTool::name).toList();

        assertThat(available).containsAll(SELLER_TOOL_NAMES);
        assertThat(available).containsAll(ADMIN_TOOL_NAMES);
    }
}
