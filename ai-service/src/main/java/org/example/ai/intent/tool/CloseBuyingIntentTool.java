package org.example.ai.intent.tool;

import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class CloseBuyingIntentTool implements AgentTool {

    @Override
    public String name() {
        return "close_buying_intent";
    }

    @Override
    public String description() {
        return "Prepare a close request for one of the current buyer's own buying intents. This tool never "
                + "changes state: the buyer must confirm the close in the application UI.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("intentId", Map.of("type", "STRING", "description", "Buying-intent UUID."));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of("intentId"));
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
        try {
            UUID intentId = UUID.fromString(String.valueOf(args.get("intentId")));
            Map<String, Object> item = Map.of(
                    "intentId", intentId,
                    "status", "CONFIRMATION_REQUIRED",
                    "confirmationRequired", true,
                    "closeEndpoint", "/api/v1/ai/buying-intents/" + intentId + "/close",
                    "closed", false,
                    "automaticOutreachAllowed", false);
            Map<String, Object> data = new LinkedHashMap<>(item);
            data.put("kind", "buying_intent_status");
            data.put("items", List.of(item));
            return ToolResult.ok(data);
        } catch (IllegalArgumentException e) {
            return ToolResult.error("A valid intentId is required", 400);
        }
    }
}
