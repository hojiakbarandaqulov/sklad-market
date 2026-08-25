package org.example.evals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai.business.service.BusinessSearchService;
import org.example.ai.business.service.SupplierRecommendationService;
import org.example.ai.business.tool.RecommendSuppliersTool;
import org.example.ai.business.tool.SearchBusinessesTool;
import org.example.ai.embedding.EmbeddingSearchService;
import org.example.ai.embedding.ProductEmbeddingRepository;
import org.example.ai.gateway.GatewayClient;
import org.example.ai.provider.ChatModelProvider;
import org.example.ai.intent.service.BuyingIntentService;
import org.example.ai.intent.tool.CloseBuyingIntentTool;
import org.example.ai.intent.tool.DraftBuyingIntentTool;
import org.example.ai.intent.tool.GetMyBuyingIntentsTool;
import org.example.ai.intent.tool.SearchBuyingIntentsTool;
import org.example.ai.matching.service.BuyerOpportunityService;
import org.example.ai.matching.tool.RecommendBuyersTool;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.CategoryResolver;
import org.example.ai.tool.ToolRegistry;
import org.example.ai.tool.impl.DraftChatReplyTool;
import org.example.ai.tool.impl.DraftLeadReplyTool;
import org.example.ai.tool.impl.DraftLeadTool;
import org.example.ai.tool.impl.FindSimilarProductsTool;
import org.example.ai.tool.impl.GetCartTool;
import org.example.ai.tool.impl.GetCatalogFiltersTool;
import org.example.ai.tool.impl.GetCompanyTool;
import org.example.ai.tool.impl.GetLeadTool;
import org.example.ai.tool.impl.GetModerationQueueTool;
import org.example.ai.tool.impl.GetMyFavoritesTool;
import org.example.ai.tool.impl.GetMyLeadsTool;
import org.example.ai.tool.impl.GetProductTool;
import org.example.ai.tool.impl.GetReportsTool;
import org.example.ai.tool.impl.GetSellerLeadsTool;
import org.example.ai.tool.impl.GetUnreadChatsTool;
import org.example.ai.tool.impl.ListCategoriesTool;
import org.example.ai.tool.impl.SearchProductsTool;
import org.example.ai.tool.impl.SemanticSearchProductsTool;
import org.example.ai.tool.impl.SummarizeModerationItemTool;
import org.example.service.ActionDraftService;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;

/** Shared fixtures for the eval harness (golden-set loader, real full tool registry, system prompt). */
final class EvalSupport {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private EvalSupport() {
    }

    /** The REAL {@link ToolRegistry} with every REAL tool instance (collaborators mocked; only name()/allowedRoles()/schema are exercised). */
    static ToolRegistry buildFullRegistry() {
        GatewayClient gateway = mock(GatewayClient.class);
        ChatModelProvider provider = mock(ChatModelProvider.class);
        ActionDraftService draftService = mock(ActionDraftService.class);
        EmbeddingSearchService searchService = mock(EmbeddingSearchService.class);
        CategoryResolver categoryResolver = mock(CategoryResolver.class);
        String model = "gemini-2.5-flash";

        List<AgentTool> tools = List.of(
                new SearchProductsTool(gateway, categoryResolver),
                new SemanticSearchProductsTool(searchService),
                new GetProductTool(gateway),
                new GetCompanyTool(gateway),
                new ListCategoriesTool(gateway),
                new GetCatalogFiltersTool(gateway, categoryResolver, mock(ProductEmbeddingRepository.class)),
                new FindSimilarProductsTool(searchService),
                new SearchBusinessesTool(mock(BusinessSearchService.class), categoryResolver),
                new RecommendSuppliersTool(mock(SupplierRecommendationService.class), categoryResolver),
                new GetCartTool(gateway),
                new GetMyLeadsTool(gateway),
                new GetMyFavoritesTool(gateway),
                new GetUnreadChatsTool(gateway),
                new GetLeadTool(gateway),
                new DraftLeadTool(gateway, draftService),
                new GetSellerLeadsTool(gateway),
                new RecommendBuyersTool(mock(BuyerOpportunityService.class)),
                new DraftBuyingIntentTool(mock(BuyingIntentService.class)),
                new GetMyBuyingIntentsTool(mock(BuyingIntentService.class)),
                new CloseBuyingIntentTool(),
                new SearchBuyingIntentsTool(mock(BuyingIntentService.class)),
                new DraftLeadReplyTool(gateway, provider, model),
                new DraftChatReplyTool(gateway, provider, model),
                new GetModerationQueueTool(gateway),
                new GetReportsTool(gateway),
                new SummarizeModerationItemTool(gateway));
        return new ToolRegistry(tools);
    }

    static JsonNode loadGoldenSet() throws Exception {
        try (InputStream in = new ClassPathResource("evals/golden-set.json").getInputStream()) {
            return MAPPER.readTree(in);
        }
    }

    static String loadSystemPrompt() throws Exception {
        return new ClassPathResource("prompts/system-agent-v5.md").getContentAsString(StandardCharsets.UTF_8);
    }

    static Set<String> asSet(JsonNode arrayNode) {
        Set<String> set = new LinkedHashSet<>();
        arrayNode.forEach(n -> set.add(n.asText()));
        return set;
    }
}
