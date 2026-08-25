package org.example.ai.tool.impl;

import org.example.ai.embedding.EmbeddingSearchService;
import org.example.ai.error.AiChatException;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.example.dto.SearchResultItem;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Semantic (embedding-based, cross-lingual) catalog search — the vector-index counterpart to the
 * keyword {@code search_products} tool. Use when keyword search is weak or empty, or when the user's
 * wording won't literally match product titles (e.g. a Russian query over Uzbek-titled products, or
 * a descriptive/conceptual query). Reads only the local {@code product_embedding} index — no
 * downstream call, no user credential needed. Role-open (any authenticated caller).
 */
@Component
public class SemanticSearchProductsTool implements AgentTool {

    public static final String NAME = "semantic_search_products";
    private static final int DEFAULT_LIMIT = 8;

    private final EmbeddingSearchService searchService;

    public SemanticSearchProductsTool(EmbeddingSearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Semantic product search over an embedding index (multilingual: a Russian query can "
                + "match an Uzbek-titled product and vice-versa). Prefer keyword search_products for "
                + "exact names/categories; use this when keyword search returns nothing useful or the "
                + "user describes what they want conceptually. Returns products ranked by meaning "
                + "similarity (name, slug, price, currency, region, score). Never invent products.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of("type", "STRING", "description",
                "What the user is looking for, in any of uz/ru/en — a phrase or description, not just keywords."));
        properties.put("limit", Map.of("type", "INTEGER", "description", "Max results, default 8, max 50."));
        properties.put("presentation", Map.of("type", "STRING", "enum", List.of("CARDS", "PLAIN_TEXT"),
                "description", "Use PLAIN_TEXT only when the user explicitly asks for no cards."));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of("query"));
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        String query = ToolArgs.asString(args.get("query"));
        if (query == null) {
            return ToolResult.error("Argument 'query' is required", 400);
        }
        int limit = Math.max(ToolArgs.asInt(args.get("limit"), DEFAULT_LIMIT), 1);
        try {
            List<SearchResultItem> items = searchService.search(query, limit);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", "business_search");
            result.put("presentation", ToolArgs.presentation(args.get("presentation")));
            result.put("items", items.stream().map(this::project).toList());
            result.put("count", items.size());
            return ToolResult.ok(result);
        } catch (AiChatException e) {
            return ToolResult.error("Semantic search is temporarily unavailable", null);
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
