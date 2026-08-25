package org.example.ai.tool.impl;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteCartDto;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.PlatformLanguage;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@code GET /api/v1/cart} (verified in {@code CartController}, class-level {@code hasRole('BUYER')}). */
@Component
public class GetCartTool implements AgentTool {

    private final GatewayClient gatewayClient;

    public GetCartTool(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    @Override
    public String name() {
        return "get_cart";
    }

    @Override
    public String description() {
        return "Fetch the current buyer's shopping cart: items, quantities, and total item count.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "OBJECT", "properties", Map.of(
                "presentation", Map.of("type", "STRING", "enum", List.of("CARDS", "PLAIN_TEXT"),
                        "description", "Use PLAIN_TEXT only when the user explicitly asks for no cards.")),
                "required", List.of());
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("BUYER");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        try {
            GatewayEnvelope<RemoteCartDto> envelope = gatewayClient.get(
                    "/api/v1/cart",
                    null,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemoteCartDto>>() {
                    });
            RemoteCartDto cart = envelope == null ? null : envelope.data();
            if (cart == null) {
                return ToolResult.ok(emptyResult(args));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", "business_search");
            result.put("presentation", ToolArgs.presentation(args.get("presentation")));
            result.put("itemCount", cart.itemCount());
            result.put("totalQuantity", cart.totalQuantity());
            result.put("items", cart.items() == null ? List.of() : cart.items().stream().map(item -> {
                Map<String, Object> projected = new LinkedHashMap<>();
                projected.put("type", "PRODUCT");
                projected.put("id", item.productId());
                projected.put("name", item.productName());
                projected.put("slug", item.productSlug());
                projected.put("productName", item.productName());
                projected.put("productSlug", item.productSlug());
                projected.put("price", item.price());
                projected.put("currency", item.currency());
                projected.put("companyName", item.companyName());
                projected.put("quantity", item.quantity());
                return projected;
            }).toList());
            return ToolResult.ok(result);
        } catch (GatewayNotFoundException e) {
            return ToolResult.ok(emptyResult(args));
        } catch (GatewayUnavailableException e) {
            return ToolResult.error("The cart service is temporarily unavailable", null);
        }
    }

    private Map<String, Object> emptyResult(Map<String, Object> args) {
        return Map.of(
                "kind", "business_search",
                "presentation", ToolArgs.presentation(args.get("presentation")),
                "itemCount", 0,
                "totalQuantity", 0,
                "items", List.of());
    }
}
