package org.example.ai.tool.impl;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteFavoriteProductDto;
import org.example.ai.gateway.dto.RemoteSpringPage;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.PlatformLanguage;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code GET /api/v1/product-favorites} (verified in {@code FavoriteController}: no
 * {@code @PreAuthorize}, just authenticated; page params {@code page}/{@code perPage}, response is
 * Jackson's default {@code PageImpl} shape).
 */
@Component
public class GetMyFavoritesTool implements AgentTool {

    private static final int PAGE_SIZE = 10;

    private final GatewayClient gatewayClient;

    public GetMyFavoritesTool(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    @Override
    public String name() {
        return "get_my_favorites";
    }

    @Override
    public String description() {
        return "List the current user's favorited/saved products.";
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
        return Set.of();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", "1");
        params.add("perPage", String.valueOf(PAGE_SIZE));

        try {
            GatewayEnvelope<RemoteSpringPage<RemoteFavoriteProductDto>> envelope = gatewayClient.get(
                    "/api/v1/product-favorites",
                    params,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemoteSpringPage<RemoteFavoriteProductDto>>>() {
                    });
            RemoteSpringPage<RemoteFavoriteProductDto> page = envelope == null ? null : envelope.data();
            List<RemoteFavoriteProductDto> content = page == null || page.content() == null ? List.of() : page.content();

            List<Map<String, Object>> projected = content.stream().map(product -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("type", "PRODUCT");
                item.put("name", product.name());
                item.put("slug", product.slug());
                item.put("price", product.price());
                item.put("currency", product.currency());
                return item;
            }).toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", "business_search");
            result.put("presentation", ToolArgs.presentation(args.get("presentation")));
            result.put("items", projected);
            result.put("favorites", projected);
            result.put("count", projected.size());
            return ToolResult.ok(result);
        } catch (GatewayNotFoundException e) {
            return ToolResult.ok(Map.of(
                    "kind", "business_search",
                    "presentation", ToolArgs.presentation(args.get("presentation")),
                    "items", List.of(),
                    "favorites", List.of(),
                    "count", 0));
        } catch (GatewayUnavailableException e) {
            return ToolResult.error("The favorites service is temporarily unavailable", null);
        }
    }
}
