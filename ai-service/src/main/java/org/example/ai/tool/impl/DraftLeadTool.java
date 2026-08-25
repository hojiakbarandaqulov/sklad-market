package org.example.ai.tool.impl;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteProductDetailDto;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.PlatformLanguage;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.example.entity.ActionDraft;
import org.example.service.ActionDraftService;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prepares a buyer lead/RFQ draft — never calls lead-service. Every product slug is re-verified
 * (existence + {@code APPROVED} status) against the real public {@code GET /api/v1/products/slug/{slug}}
 * before it can appear in a draft (PLAN.md §4.2 item 5 / T8: never trust model-supplied ids), and
 * the persisted {@code action_draft.payload} mirrors {@code LeadCreateRequest} field-for-field
 * (verified in {@code lead-service}'s source, PLAN.md §7 item 14) so
 * {@link org.example.service.ActionDraftConfirmService} can forward it to {@code POST
 * /api/v1/leads} unmodified except for buyer-editable contact-field overrides.
 *
 * <p>{@code LeadCreateRequest.create()} attributes the whole lead to the FIRST resolved product's
 * seller/company (verified in {@code LeadServiceImpl}) — there is no per-item seller splitting on
 * that endpoint. To avoid silently misattributing items to the wrong seller, this tool requires
 * every requested product to belong to the same company; multi-seller requests are rejected with a
 * clear error asking the user to split them into one request per seller.
 */
@Component
public class DraftLeadTool implements AgentTool {

    public static final String NAME = "draft_lead";

    private static final int MAX_ITEMS = 20;

    private final GatewayClient gatewayClient;
    private final ActionDraftService actionDraftService;

    public DraftLeadTool(GatewayClient gatewayClient, ActionDraftService actionDraftService) {
        this.gatewayClient = gatewayClient;
        this.actionDraftService = actionDraftService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Prepare a draft buyer lead/RFQ (request for quote) to ONE seller company for one or "
                + "more products, using exact slugs from search_products/get_product. This does NOT "
                + "submit or send anything — it only creates a draft that is shown to the user for "
                + "review; the user must explicitly confirm it in the app before the seller sees "
                + "anything. All requested products must belong to the same seller company; if the "
                + "user wants products from different sellers, call this tool once per seller. "
                + "Requires the user's contact name and phone.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("productSlugs", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"), "description",
                "Exact product slugs (from search_products/get_product), all from the same seller company."));
        properties.put("quantity", Map.of("type", "INTEGER", "description",
                "Requested quantity, applied to every item in the request. Defaults to 1."));
        properties.put("contactName", Map.of("type", "STRING", "description", "Buyer's contact name."));
        properties.put("contactPhone", Map.of("type", "STRING", "description", "Buyer's contact phone number."));
        properties.put("contactEmail", Map.of("type", "STRING", "description", "Buyer's contact email (optional)."));
        properties.put("deliveryAddress", Map.of("type", "STRING", "description", "Delivery address (optional)."));
        properties.put("neededDate", Map.of("type", "STRING", "description", "Date needed by, ISO format YYYY-MM-DD (optional)."));
        properties.put("comment", Map.of("type", "STRING", "description", "Free-text comment for the seller (optional)."));
        return Map.of("type", "OBJECT", "properties", properties,
                "required", List.of("productSlugs", "contactName", "contactPhone"));
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("BUYER");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        List<String> slugs = dedupe(ToolArgs.asStringList(args.get("productSlugs")));
        if (slugs.isEmpty()) {
            return ToolResult.error("productSlugs must contain at least one product slug", 400);
        }
        if (slugs.size() > MAX_ITEMS) {
            return ToolResult.error("A single request supports at most " + MAX_ITEMS + " products", 400);
        }
        String contactName = ToolArgs.asString(args.get("contactName"));
        String contactPhone = ToolArgs.asString(args.get("contactPhone"));
        if (contactName == null || contactPhone == null) {
            return ToolResult.error("contactName and contactPhone are required", 400);
        }
        int quantity = ToolArgs.asInt(args.get("quantity"), 1);
        if (quantity < 1) {
            return ToolResult.error("quantity must be at least 1", 400);
        }
        String neededDate = ToolArgs.asString(args.get("neededDate"));
        if (neededDate != null) {
            try {
                LocalDate.parse(neededDate);
            } catch (DateTimeParseException e) {
                return ToolResult.error("neededDate must be an ISO date (YYYY-MM-DD)", 400);
            }
        }

        List<RemoteProductDetailDto> products = new ArrayList<>(slugs.size());
        for (String slug : slugs) {
            try {
                GatewayEnvelope<RemoteProductDetailDto> envelope = gatewayClient.get(
                        "/api/v1/products/slug/{slug}",
                        null,
                        context.bearerToken(),
                        PlatformLanguage.header(context.acceptLanguage()),
                        new ParameterizedTypeReference<GatewayEnvelope<RemoteProductDetailDto>>() {
                        },
                        slug);
                RemoteProductDetailDto product = envelope == null ? null : envelope.data();
                if (product == null || product.id() == null) {
                    return ToolResult.notFound("Product not found: " + slug);
                }
                if (!"APPROVED".equals(product.status())) {
                    return ToolResult.error("Product is not currently available: " + slug, 400);
                }
                if (product.company() == null || product.company().id() == null) {
                    return ToolResult.error("Product has no seller company on record: " + slug, 400);
                }
                products.add(product);
            } catch (GatewayNotFoundException e) {
                return ToolResult.notFound("Product not found: " + slug);
            } catch (GatewayUnavailableException e) {
                return ToolResult.error("The product service is temporarily unavailable", null);
            }
        }

        Long companyId = products.get(0).company().id();
        boolean singleSeller = products.stream().allMatch(p -> companyId.equals(p.company().id()));
        if (!singleSeller) {
            return ToolResult.error(
                    "These products belong to different seller companies. Create one request per seller.", 400);
        }

        Map<String, Object> payload = buildPayload(products, quantity, contactName, contactPhone,
                ToolArgs.asString(args.get("contactEmail")), ToolArgs.asString(args.get("deliveryAddress")),
                neededDate, ToolArgs.asString(args.get("comment")));

        ActionDraft draft = actionDraftService.create(context.conversationId(), context.userSub(), "LEAD", payload);

        Map<String, Object> data = new LinkedHashMap<>(payload);
        data.put("draftId", draft.getId().toString());
        data.put("draftType", "LEAD");
        data.put("draftPayload", payload);
        data.put("status", "DRAFT");
        data.put("expiresAt", draft.getExpiresAt().toString());
        return ToolResult.ok(data);
    }

    private List<String> dedupe(List<String> slugs) {
        return new ArrayList<>(new LinkedHashSet<>(slugs));
    }

    private Map<String, Object> buildPayload(
            List<RemoteProductDetailDto> products,
            int quantity,
            String contactName,
            String contactPhone,
            String contactEmail,
            String deliveryAddress,
            String neededDate,
            String comment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (products.size() == 1) {
            payload.put("source", "PRODUCT");
            payload.put("productId", products.get(0).id());
        } else {
            payload.put("source", "CART");
            payload.put("productIds", products.stream().map(RemoteProductDetailDto::id).toList());
        }
        payload.put("quantity", quantity);
        payload.put("contactName", contactName);
        payload.put("contactPhone", contactPhone);
        payload.put("contactEmail", contactEmail);
        payload.put("deliveryAddress", deliveryAddress);
        payload.put("neededDate", neededDate);
        payload.put("comment", comment);

        List<Map<String, Object>> items = products.stream().map(p -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", p.name());
            item.put("slug", p.slug());
            item.put("price", p.price());
            item.put("currency", p.currency());
            return (Map<String, Object>) item;
        }).toList();
        payload.put("items", items);
        payload.put("companyName", products.get(0).company().name());
        payload.put("companySlug", products.get(0).company().slug());
        return payload;
    }
}
