package org.example.ai.matching.tool;

import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.matching.dto.BuyerOpportunity;
import org.example.ai.matching.dto.BuyerOpportunityResult;
import org.example.ai.matching.service.BuyerOpportunityService;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class RecommendBuyersTool implements AgentTool {

    private static final List<String> STATUSES = List.of("NEW", "VIEWED", "CONTACTED", "CLOSED", "CANCELED");

    private final BuyerOpportunityService service;

    public RecommendBuyersTool(BuyerOpportunityService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "recommend_buyers";
    }

    @Override
    public String description() {
        return "Rank buyer opportunities already authorized for the current seller. Candidates come only "
                + "from the seller's own leads; no contacts or messages are returned and no outreach is performed.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of("type", "STRING",
                "description", "Optional product or business-need words used to rank matching requests."));
        properties.put("statuses", Map.of("type", "ARRAY", "items", Map.of("type", "STRING", "enum", STATUSES),
                "description", "Lead statuses to include. Defaults to NEW, VIEWED, and CONTACTED."));
        properties.put("limit", Map.of("type", "INTEGER", "minimum", 1, "maximum", 20));
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
        List<String> statuses = ToolArgs.asStringList(args.get("statuses")).stream()
                .map(status -> status.toUpperCase(Locale.ROOT)).toList();
        try {
            BuyerOpportunityResult result = service.recommend(
                    context.bearerToken(), context.acceptLanguage(), ToolArgs.asString(args.get("query")),
                    statuses, ToolArgs.asInt(args.get("limit"), 10));
            List<Map<String, Object>> opportunities = new ArrayList<>();
            for (BuyerOpportunity opportunity : result.opportunities()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("leadId", opportunity.leadId());
                item.put("status", opportunity.status());
                item.put("neededDate", opportunity.neededDate());
                item.put("matchScore", opportunity.matchScore());
                item.put("reasons", opportunity.reasons());
                item.put("requestedItems", opportunity.requestedItems());
                item.put("nextAction", opportunity.nextAction());
                item.put("automaticOutreachAllowed", false);
                opportunities.add(item);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("kind", "buyer_recommendations");
            data.put("items", opportunities);
            data.put("opportunities", opportunities);
            data.put("count", opportunities.size());
            data.put("evaluatedLeadCount", result.evaluatedLeadCount());
            data.put("totalLeadCount", result.totalLeadCount());
            data.put("candidatesTruncated", result.candidatesTruncated());
            data.put("asOf", result.asOf());
            data.put("source", result.source());
            data.put("privacy", result.privacy());
            data.put("automaticOutreachAllowed", false);
            return ToolResult.ok(data);
        } catch (GatewayUnavailableException e) {
            return ToolResult.error("The requests service is temporarily unavailable", 503);
        }
    }
}
