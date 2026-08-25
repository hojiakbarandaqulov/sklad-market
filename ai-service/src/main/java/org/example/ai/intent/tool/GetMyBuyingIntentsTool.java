package org.example.ai.intent.tool;

import org.example.ai.intent.dto.BuyingIntentResponse;
import org.example.ai.intent.service.BuyingIntentService;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.example.dto.PagedResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class GetMyBuyingIntentsTool implements AgentTool {

    private static final List<String> STATUSES = List.of("DRAFT", "PUBLISHED", "CLOSED", "EXPIRED");

    private final BuyingIntentService service;

    public GetMyBuyingIntentsTool(BuyingIntentService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "get_my_buying_intents";
    }

    @Override
    public String description() {
        return "List only the current buyer's own buying-intent drafts and publications.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("status", Map.of("type", "STRING", "enum", STATUSES,
                "description", "Optional lifecycle-status filter."));
        properties.put("limit", Map.of("type", "INTEGER", "minimum", 1, "maximum", 50));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of());
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("BUYER");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        if (context == null || context.roles() == null || !context.roles().contains("BUYER")) {
            return ToolResult.error("Buyer role is required", 403);
        }
        String status = ToolArgs.asString(args.get("status"));
        if (status != null) {
            status = status.toUpperCase(Locale.ROOT);
        }
        int limit = Math.max(1, Math.min(ToolArgs.asInt(args.get("limit"), 20), 50));
        PagedResponse<BuyingIntentResponse> page = service.listOwn(context.userSub(), 1, limit, status);
        List<BuyingIntentResponse> intents = page.getItems();
        List<Map<String, Object>> projected = new ArrayList<>();
        for (BuyingIntentResponse intent : intents) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("intentId", intent.id());
            item.put("status", intent.status());
            item.put("category", intent.category());
            item.put("region", intent.region());
            item.put("need", intent.needText());
            item.put("quantity", intent.quantity());
            item.put("quantityUnit", intent.quantityUnit());
            item.put("budgetMin", intent.budgetMin());
            item.put("budgetMax", intent.budgetMax());
            item.put("currency", intent.currency());
            item.put("expiresAt", intent.expiresAt());
            item.put("contactAvailable", false);
            item.put("contactAccess", "NOT_COLLECTED");
            item.put("automaticOutreachAllowed", false);
            projected.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", "buying_intents");
        data.put("items", projected);
        data.put("intents", projected);
        data.put("count", projected.size());
        data.put("page", page.getMeta().getPage());
        data.put("perPage", page.getMeta().getPerPage());
        data.put("total", page.getMeta().getTotal());
        data.put("totalPages", page.getMeta().getTotalPages());
        return ToolResult.ok(data);
    }
}
