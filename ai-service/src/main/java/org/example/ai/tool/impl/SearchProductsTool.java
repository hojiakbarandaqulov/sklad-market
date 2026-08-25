package org.example.ai.tool.impl;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemotePagedResponse;
import org.example.ai.gateway.dto.RemoteProductDto;
import org.example.ai.gateway.dto.RemoteProductImageDto;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.CategoryResolver;
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
import java.util.Optional;
import java.util.Set;

/**
 * {@code GET /api/v1/catalog} (verified live in {@code CatalogController}/{@code
 * CatalogServiceImpl} on {@code main}, 2026-07-08): {@code category} is parsed via
 * {@code Long.valueOf(category)} — a numeric category id, not a slug (PLAN.md §7 item 7, still
 * true) — so a {@code categorySlug} arg must be resolved via {@link CategoryResolver} first. The
 * endpoint has no {@code minPrice}/{@code maxPrice}/{@code saleType} params (§7 item 8, still
 * true), so this tool does not advertise or simulate those filters over one fetched page. Results
 * and totals always describe the same server-side query, which filters to approved, active items.
 */
@Component
public class SearchProductsTool implements AgentTool {

    private static final int PAGE_SIZE = 10;
    private static final int MAX_DESCRIPTION_CHARS = 160;

    private final GatewayClient gatewayClient;
    private final CategoryResolver categoryResolver;

    public SearchProductsTool(GatewayClient gatewayClient, CategoryResolver categoryResolver) {
        this.gatewayClient = gatewayClient;
        this.categoryResolver = categoryResolver;
    }

    @Override
    public String name() {
        return "search_products";
    }

    @Override
    public String description() {
        return "Search the Sklad Market product catalog by free-text query and/or category. Returns a "
                + "page of matching approved, active products "
                + "(name, slug, price, currency, region, short description). Always use this before "
                + "answering any question about real products, prices, or availability — never invent "
                + "product data.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of("type", "STRING", "description",
                "Free-text search over the product name (Cyrillic or Latin, any of uz/ru/en)."));
        properties.put("categorySlug", Map.of("type", "STRING", "description",
                "Category slug to filter by, obtained from list_categories."));
        properties.put("page", Map.of("type", "INTEGER", "description", "1-based page number, default 1."));
        properties.put("presentation", Map.of("type", "STRING", "enum", List.of("CARDS", "PLAIN_TEXT"),
                "description", "Use PLAIN_TEXT only when the user explicitly asks for no cards."));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of());
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        String query = ToolArgs.asString(args.get("query"));
        String categorySlug = ToolArgs.asString(args.get("categorySlug"));
        int page = Math.max(ToolArgs.asInt(args.get("page"), 1), 1);

        Long categoryId = null;
        if (categorySlug != null) {
            Optional<Long> resolved = categoryResolver.resolve(categorySlug, context);
            if (resolved.isEmpty()) {
                return ToolResult.notFound("Category not found: " + categorySlug);
            }
            categoryId = resolved.get();
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (query != null) {
            params.add("q", query);
        }
        if (categoryId != null) {
            params.add("category", String.valueOf(categoryId));
        }
        params.add("page", String.valueOf(page));
        params.add("perPage", String.valueOf(PAGE_SIZE));

        try {
            GatewayEnvelope<RemotePagedResponse<RemoteProductDto>> envelope = gatewayClient.get(
                    "/api/v1/catalog",
                    params,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemotePagedResponse<RemoteProductDto>>>() {
                    });
            RemotePagedResponse<RemoteProductDto> paged = envelope == null ? null : envelope.data();
            List<RemoteProductDto> items = paged == null || paged.items() == null ? List.of() : paged.items();

            List<Map<String, Object>> projected = items.stream().limit(PAGE_SIZE).map(this::project).toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", "business_search");
            result.put("presentation", ToolArgs.presentation(args.get("presentation")));
            result.put("items", projected);
            result.put("count", projected.size());
            result.put("page", page);
            result.put("totalAvailable", paged != null && paged.meta() != null ? paged.meta().total() : projected.size());
            return ToolResult.ok(result);
        } catch (GatewayNotFoundException e) {
            return ToolResult.ok(Map.of(
                    "kind", "business_search",
                    "presentation", ToolArgs.presentation(args.get("presentation")),
                    "items", List.of(),
                    "count", 0,
                    "page", page,
                    "totalAvailable", 0));
        } catch (GatewayUnavailableException e) {
            return ToolResult.error("The catalog is temporarily unavailable", null);
        }
    }

    private Map<String, Object> project(RemoteProductDto product) {
        Map<String, Object> projected = new LinkedHashMap<>();
        projected.put("type", "PRODUCT");
        projected.put("id", product.id());
        projected.put("name", product.name());
        projected.put("slug", product.slug());
        projected.put("price", product.price());
        projected.put("currency", product.currency());
        projected.put("regionId", product.regionId());
        projected.put("imageUrl", RemoteProductImageDto.primaryCardUrl(product.images()));
        projected.put("shortDescription", ToolArgs.truncate(product.shortDescription(), MAX_DESCRIPTION_CHARS));
        return projected;
    }
}
