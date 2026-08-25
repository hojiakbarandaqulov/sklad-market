package org.example.ai.tool.impl;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteCatalogFiltersDto;
import org.example.ai.embedding.ProductEmbeddingRepository;
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
 * {@code GET /api/v1/catalog/filters} (verified live in {@code CatalogController}/{@code
 * CatalogServiceImpl} on {@code main}): like {@code /catalog}, {@code category} is a numeric id
 * parsed via {@code Long.valueOf(category)} — resolved from {@code categorySlug} first. An
 * unresolvable category returns an all-zero/empty response server-side rather than an error, so
 * this tool distinguishes that case itself (via {@link CategoryResolver}) to give the model an
 * explicit "category not found" instead of misleadingly empty filters.
 */
@Component
public class GetCatalogFiltersTool implements AgentTool {

    private static final int MAX_ATTRIBUTE_KEYS = 10;
    private static final int MAX_ATTRIBUTE_VALUES = 8;
    private static final int MAX_REGION_IDS = 20;

    private final GatewayClient gatewayClient;
    private final CategoryResolver categoryResolver;
    private final ProductEmbeddingRepository embeddingRepository;

    public GetCatalogFiltersTool(
            GatewayClient gatewayClient,
            CategoryResolver categoryResolver,
            ProductEmbeddingRepository embeddingRepository) {
        this.gatewayClient = gatewayClient;
        this.categoryResolver = categoryResolver;
        this.embeddingRepository = embeddingRepository;
    }

    @Override
    public String name() {
        return "get_catalog_filters";
    }

    @Override
    public String description() {
        return "Get indexed product region ids and attribute filters for the catalog, "
                + "optionally scoped to one category. Use this to tell a buyer what filters or "
                + "attributes exist before they narrow a search. Region ids are explicitly marked "
                + "as derived from the AI product index or unavailable. A catalog-wide price range "
                + "is intentionally omitted because products can use different currencies.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = Map.of("categorySlug", Map.of("type", "STRING", "description",
                "Optional category slug to scope the filters to, from list_categories."));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of());
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        String categorySlug = ToolArgs.asString(args.get("categorySlug"));
        Long categoryId = null;
        if (categorySlug != null) {
            Optional<Long> resolved = categoryResolver.resolve(categorySlug, context);
            if (resolved.isEmpty()) {
                return ToolResult.notFound("Category not found: " + categorySlug);
            }
            categoryId = resolved.get();
        }

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        if (categoryId != null) {
            params.add("category", String.valueOf(categoryId));
        }

        try {
            GatewayEnvelope<RemoteCatalogFiltersDto> envelope = gatewayClient.get(
                    "/api/v1/catalog/filters",
                    params,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemoteCatalogFiltersDto>>() {
                    });
            RemoteCatalogFiltersDto filters = envelope == null ? null : envelope.data();
            return ToolResult.ok(project(filters, categoryId));
        } catch (GatewayUnavailableException e) {
            return ToolResult.error("Catalog filters are temporarily unavailable", null);
        }
    }

    private Map<String, Object> project(RemoteCatalogFiltersDto filters, Long categoryId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (filters == null) {
            result.put("attributes", Map.of());
        } else {
            result.put("attributes", capAttributes(filters.attributes()));
        }
        result.put("priceRangeAvailable", false);
        result.put("priceRangeReason", "currency_not_scoped");
        try {
            List<Long> indexedRegionIds = embeddingRepository.findDistinctRegionIds(categoryId, MAX_REGION_IDS);
            result.put("regionIds", indexedRegionIds == null
                    ? List.of()
                    : indexedRegionIds.stream().limit(MAX_REGION_IDS).toList());
            result.put("regionIdsAvailable", true);
            result.put("regionIdsSource", "ai_product_index");
        } catch (RuntimeException unavailable) {
            result.put("regionIds", List.of());
            result.put("regionIdsAvailable", false);
            result.put("regionIdsSource", "unavailable");
        }
        return result;
    }

    private Map<String, List<String>> capAttributes(Map<String, List<String>> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> capped = new LinkedHashMap<>();
        attributes.entrySet().stream().limit(MAX_ATTRIBUTE_KEYS).forEach(entry ->
                capped.put(entry.getKey(), entry.getValue() == null ? List.of() : entry.getValue().stream().limit(MAX_ATTRIBUTE_VALUES).toList()));
        return capped;
    }
}
