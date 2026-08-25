package org.example.ai.intent.tool;

import org.example.ai.intent.dto.BuyingIntentMatchResponse;
import org.example.ai.intent.dto.BuyingIntentMatchResult;
import org.example.ai.intent.service.BuyingIntentService;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SearchBuyingIntentsTool implements AgentTool {

    private final BuyingIntentService service;

    public SearchBuyingIntentsTool(BuyingIntentService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "search_buying_intents";
    }

    @Override
    public String description() {
        return "Search buyer-opted-in buying needs by category, region, or business need. Owner/contact "
                + "columns are excluded, but buyer-published free text is seller-visible and best-effort "
                + "screening cannot guarantee anonymity. This tool cannot initiate outreach.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("category", Map.of("type", "STRING", "description", "Exact product category, optional."));
        properties.put("region", Map.of("type", "STRING", "description", "Exact target region, optional."));
        properties.put("query", Map.of("type", "STRING", "description", "Words describing the seller's relevant offer, optional."));
        properties.put("limit", Map.of("type", "INTEGER", "minimum", 1, "maximum", 50));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of());
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("SELLER");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        if (context == null || context.roles() == null || !context.roles().contains("SELLER")) {
            return ToolResult.error("Seller role is required", 403);
        }
        BuyingIntentMatchResult result = service.searchPublished(
                ToolArgs.asString(args.get("category")),
                ToolArgs.asString(args.get("region")),
                ToolArgs.asString(args.get("query")),
                ToolArgs.asInt(args.get("limit"), 10));
        List<BuyingIntentMatchResponse> matches = result.items();
        List<Map<String, Object>> projected = new ArrayList<>();
        for (BuyingIntentMatchResponse match : matches) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("intentId", match.intentId());
            item.put("category", match.category());
            item.put("region", match.region());
            item.put("need", match.needText());
            item.put("quantity", match.quantity());
            item.put("quantityUnit", match.quantityUnit());
            item.put("budgetMin", match.budgetMin());
            item.put("budgetMax", match.budgetMax());
            item.put("currency", match.currency());
            item.put("expiresAt", match.expiresAt());
            item.put("matchScore", match.matchScore());
            item.put("reasons", match.reasons());
            item.put("contactAvailable", false);
            item.put("contactAccess", "NOT_COLLECTED");
            item.put("automaticOutreachAllowed", false);
            projected.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", "buying_intent_matches");
        data.put("items", projected);
        data.put("matches", projected);
        data.put("count", projected.size());
        data.put("evaluatedIntentCount", result.evaluatedIntentCount());
        data.put("totalIntentCount", result.totalIntentCount());
        data.put("candidatesTruncated", result.candidatesTruncated());
        data.put("asOf", result.asOf());
        data.put("privacy", result.privacy());
        data.put("sellerVisibleUserText", true);
        data.put("privacyScreening", "BEST_EFFORT");
        data.put("automaticOutreachAllowed", false);
        return ToolResult.ok(data);
    }
}
