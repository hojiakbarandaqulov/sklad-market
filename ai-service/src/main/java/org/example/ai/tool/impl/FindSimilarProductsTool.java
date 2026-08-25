package org.example.ai.tool.impl;

import org.example.ai.embedding.EmbeddingSearchService;
import org.example.ai.error.AiChatException;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.example.dto.SearchResultItem;
import org.example.exception.AiNotFoundException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Content-based "similar products" — vector neighbours of a product the user is looking at or asked
 * about, with a same-category boost. Takes the product's {@code slug} (which the model gets from
 * search results / get_product), resolved server-side against the index; the model's slug is never
 * trusted for a write, only for a read-only lookup that returns "not found" if it isn't indexed.
 * Reads only the local index — no downstream call. Role-open (any authenticated caller).
 */
@Component
public class FindSimilarProductsTool implements AgentTool {

    public static final String NAME = "find_similar_products";
    private static final int DEFAULT_LIMIT = 6;

    private final EmbeddingSearchService searchService;

    public FindSimilarProductsTool(EmbeddingSearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Find products similar to a given product (by meaning, from the embedding index, with "
                + "a same-category preference). Pass the product's slug, obtained from search_products, "
                + "semantic_search_products, or get_product. Returns similar products (name, slug, "
                + "price, currency, region, score). Use for 'show me products like X' / alternatives.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("slug", Map.of("type", "STRING", "description",
                "The product slug to find neighbours of (from a prior search or get_product result)."));
        properties.put("limit", Map.of("type", "INTEGER", "description", "Max results, default 6, max 50."));
        properties.put("presentation", Map.of("type", "STRING", "enum", List.of("CARDS", "PLAIN_TEXT"),
                "description", "Use PLAIN_TEXT only when the user explicitly asks for no cards."));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of("slug"));
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        String slug = ToolArgs.asString(args.get("slug"));
        if (slug == null) {
            return ToolResult.error("Argument 'slug' is required", 400);
        }
        int limit = Math.max(ToolArgs.asInt(args.get("limit"), DEFAULT_LIMIT), 1);
        try {
            List<SearchResultItem> items = searchService.similarBySlug(slug, limit);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", "business_search");
            result.put("presentation", ToolArgs.presentation(args.get("presentation")));
            result.put("items", items.stream().map(this::project).toList());
            result.put("count", items.size());
            return ToolResult.ok(result);
        } catch (AiNotFoundException e) {
            return ToolResult.notFound("That product is not in the search index yet: " + slug);
        } catch (AiChatException e) {
            return ToolResult.error("Similar-products lookup is temporarily unavailable", null);
        }
    }

    private Map<String, Object> project(SearchResultItem item) {
        Map<String, Object> projected = new LinkedHashMap<>();
        projected.put("type", "PRODUCT");
        projected.put("id", item.productId());
        projected.put("name", item.name());
        projected.put("slug", item.slug());
        projected.put("price", item.price());
        projected.put("currency", item.currency());
        projected.put("regionId", item.regionId());
        projected.put("imageUrl", item.imageUrl());
        projected.put("score", item.score());
        return projected;
    }
}
