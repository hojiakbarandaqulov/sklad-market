package org.example.ai.intent.tool;

import org.example.ai.intent.dto.BuyingIntentRequest;
import org.example.ai.intent.dto.BuyingIntentResponse;
import org.example.ai.intent.service.BuyingIntentService;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class DraftBuyingIntentTool implements AgentTool {

    private final BuyingIntentService service;

    public DraftBuyingIntentTool(BuyingIntentService service) {
        this.service = service;
    }

    @Override
    public String name() {
        return "draft_buying_intent";
    }

    @Override
    public String description() {
        return "Create a private buying-intent draft for the current buyer. It is not visible to sellers "
                + "until the buyer explicitly consents to seller-visible text outside the model tool loop. "
                + "Contact screening is best-effort, so never include identifying details.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("category", Map.of("type", "STRING", "description", "Required product category."));
        properties.put("region", Map.of("type", "STRING", "description", "Target region, optional."));
        properties.put("needText", Map.of("type", "STRING",
                "description", "Required business need without names, phone numbers, emails, addresses, or URLs."));
        properties.put("quantity", Map.of("type", "NUMBER", "exclusiveMinimum", 0));
        properties.put("quantityUnit", Map.of("type", "STRING"));
        properties.put("budgetMin", Map.of("type", "NUMBER", "minimum", 0));
        properties.put("budgetMax", Map.of("type", "NUMBER", "minimum", 0));
        properties.put("currency", Map.of("type", "STRING", "description", "Three-letter currency code; defaults to UZS."));
        properties.put("validForDays", Map.of("type", "INTEGER", "minimum", 1, "maximum", 90,
                "description", "How long the intent remains valid; defaults to 30 days."));
        return Map.of("type", "OBJECT", "properties", properties,
                "required", List.of("category", "needText"));
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
        String category = ToolArgs.asString(args.get("category"));
        String needText = ToolArgs.asString(args.get("needText"));
        if (category == null || needText == null) {
            return ToolResult.error("category and needText are required", 400);
        }
        int validForDays = Math.max(1, Math.min(ToolArgs.asInt(args.get("validForDays"), 30), 90));
        try {
            BuyingIntentRequest request = new BuyingIntentRequest(
                    category,
                    ToolArgs.asString(args.get("region")),
                    needText,
                    decimal(args.get("quantity")),
                    ToolArgs.asString(args.get("quantityUnit")),
                    decimal(args.get("budgetMin")),
                    decimal(args.get("budgetMax")),
                    currency(args.get("currency")),
                    Instant.now().plus(Duration.ofDays(validForDays)));
            BuyingIntentResponse draft = service.createDraft(context.userSub(), request);
            Map<String, Object> draftItem = new LinkedHashMap<>();
            draftItem.put("intentId", draft.id());
            draftItem.put("status", draft.status());
            draftItem.put("category", draft.category());
            draftItem.put("region", draft.region());
            draftItem.put("need", draft.needText());
            draftItem.put("quantity", draft.quantity());
            draftItem.put("quantityUnit", draft.quantityUnit());
            draftItem.put("budgetMin", draft.budgetMin());
            draftItem.put("budgetMax", draft.budgetMax());
            draftItem.put("currency", draft.currency());
            draftItem.put("expiresAt", draft.expiresAt());
            draftItem.put("publicationDisclosure", draft.publicationDisclosure());
            draftItem.put("contactAvailable", false);
            draftItem.put("contactAccess", "NOT_COLLECTED");
            draftItem.put("automaticOutreachAllowed", false);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("kind", "buying_intent_draft");
            data.put("items", List.of(draftItem));
            data.put("intentId", draft.id());
            data.put("status", draft.status());
            data.put("category", draft.category());
            data.put("region", draft.region());
            data.put("need", draft.needText());
            data.put("quantity", draft.quantity());
            data.put("quantityUnit", draft.quantityUnit());
            data.put("budgetMin", draft.budgetMin());
            data.put("budgetMax", draft.budgetMax());
            data.put("currency", draft.currency());
            data.put("expiresAt", draft.expiresAt());
            data.put("contactAvailable", false);
            data.put("contactAccess", "NOT_COLLECTED");
            data.put("requiresPublicationConfirmation", true);
            data.put("confirmEndpoint", "/api/v1/ai/buying-intents/" + draft.id() + "/publish");
            data.put("confirmBody", Map.of("publicationConsent", true));
            data.put("publicationDisclosure", draft.publicationDisclosure());
            data.put("automaticOutreachAllowed", false);
            return ToolResult.ok(data);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage(), 400);
        }
    }

    private BigDecimal decimal(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        if (!Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("Numeric values must be finite");
        }
        return new BigDecimal(number.toString());
    }

    private String currency(Object value) {
        String currency = ToolArgs.asString(value);
        return currency == null ? "UZS" : currency.toUpperCase(Locale.ROOT);
    }
}
