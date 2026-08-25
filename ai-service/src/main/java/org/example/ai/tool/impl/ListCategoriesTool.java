package org.example.ai.tool.impl;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteCategoryDto;
import org.example.ai.gateway.dto.RemoteSpringPage;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.PlatformLanguage;
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
 * {@code GET /api/v1/categories} (verified live in {@code CategoryController}/{@code
 * CategoryServiceImpl} on {@code main}, page is 0-based per the controller's
 * {@code @RequestParam(defaultValue = "0")}). Confirmed still true (PLAN.md §7 item 9): the list
 * endpoint does NOT filter {@code isActive} (plain {@code findAll}), unlike the by-slug endpoint
 * (which filters via {@code findBySlugAndIsActiveTrue}) — so inactive categories are filtered out
 * here in the tool. Also confirmed: the list endpoint only populates the ONE {@code nameXx} field
 * matching the request's {@code Accept-Language}, so {@code Accept-Language} is set from the
 * conversation locale before projecting.
 */
@Component
public class ListCategoriesTool implements AgentTool {

    private static final int PAGE_SIZE = 100;

    private final GatewayClient gatewayClient;

    public ListCategoriesTool(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    @Override
    public String name() {
        return "list_categories";
    }

    @Override
    public String description() {
        return "List active marketplace categories (name and slug), in display order. Use this to "
                + "discover valid categorySlug values for search_products/get_catalog_filters, or to "
                + "answer what product categories exist on Sklad Market.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "OBJECT", "properties", Map.of(), "required", List.of());
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", "0");
        params.add("size", String.valueOf(PAGE_SIZE));

        try {
            GatewayEnvelope<RemoteSpringPage<RemoteCategoryDto>> envelope = gatewayClient.get(
                    "/api/v1/categories",
                    params,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemoteSpringPage<RemoteCategoryDto>>>() {
                    });
            RemoteSpringPage<RemoteCategoryDto> page = envelope == null ? null : envelope.data();
            List<RemoteCategoryDto> content = page == null || page.content() == null ? List.of() : page.content();

            List<Map<String, Object>> projected = content.stream()
                    .filter(category -> !Boolean.FALSE.equals(category.isActive()))
                    .map(category -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("name", category.displayName(context.acceptLanguage()));
                        item.put("slug", category.slug());
                        return item;
                    })
                    .toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("categories", projected);
            result.put("count", projected.size());
            return ToolResult.ok(result);
        } catch (GatewayNotFoundException e) {
            return ToolResult.ok(Map.of("categories", List.of(), "count", 0));
        } catch (GatewayUnavailableException e) {
            return ToolResult.error("The category service is temporarily unavailable", null);
        }
    }
}
