package org.example.ai.business.tool;

import org.example.ai.business.dto.BusinessResultType;
import org.example.ai.business.dto.BusinessSearchCriteria;
import org.example.ai.business.dto.BusinessSearchResponse;
import org.example.ai.business.service.BusinessSearchService;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.CategoryResolver;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class SearchBusinessesTool implements AgentTool {

    public static final String NAME = "search_businesses";

    private final BusinessSearchService service;
    private final CategoryResolver categoryResolver;

    public SearchBusinessesTool(BusinessSearchService service, CategoryResolver categoryResolver) {
        this.service = service;
        this.categoryResolver = categoryResolver;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Search indexed Sklad Market products and supplier companies using multilingual hybrid search. "
                + "Company results may include only public business phone, website, and address "
                + "fields. Verification and catalog state are historical as of the returned index freshness. "
                + "Use this for company, product, supplier, or business-contact discovery. Scores are relevance "
                + "signals, never guarantees.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of("type", "STRING", "description", "What product or business is needed."));
        properties.put("types", Map.of("type", "ARRAY", "items", Map.of("type", "STRING",
                "enum", List.of("PRODUCT", "COMPANY")), "description", "Optional result types."));
        properties.put("categorySlug", Map.of("type", "STRING", "description", "Optional category slug."));
        properties.put("regionId", Map.of("type", "INTEGER", "description", "Optional product-derived region id."));
        properties.put("minPrice", Map.of("type", "NUMBER", "description",
                "Optional minimum individual-product price; does not filter companies. Requires currency."));
        properties.put("maxPrice", Map.of("type", "NUMBER", "description",
                "Optional maximum individual-product price; does not filter companies. Requires currency."));
        properties.put("currency", Map.of("type", "STRING", "description",
                "Three-letter currency required with minPrice/maxPrice, for example UZS or USD."));
        properties.put("limit", Map.of("type", "INTEGER", "description", "Maximum results, default 10, max 30."));
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
        if (query == null) return ToolResult.error("Argument 'query' is required", 400);
        String categorySlug = ToolArgs.asString(args.get("categorySlug"));
        Long categoryId = null;
        if (categorySlug != null) {
            categoryId = categoryResolver.resolve(categorySlug, context).orElse(null);
            if (categoryId == null) return ToolResult.notFound("Category not found: " + categorySlug);
        }
        try {
            BusinessSearchResponse response = service.search(new BusinessSearchCriteria(
                    query, parseTypes(args.get("types")), categoryId,
                    asLong(args.get("regionId")), ToolArgs.asDouble(args.get("minPrice")),
                    ToolArgs.asDouble(args.get("maxPrice")), ToolArgs.asString(args.get("currency")),
                    ToolArgs.asInt(args.get("limit"), 10)), context);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("kind", "business_search");
            data.put("presentation", ToolArgs.presentation(args.get("presentation")));
            data.put("query", response.query());
            data.put("count", response.count());
            data.put("items", response.items());
            data.put("scoreMeaning", response.scoreMeaning());
            data.put("indexFreshness", response.indexFreshness());
            return ToolResult.ok(data);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage(), 400);
        } catch (RuntimeException e) {
            return ToolResult.error("Business search is temporarily unavailable", null);
        }
    }

    private Set<BusinessResultType> parseTypes(Object value) {
        List<String> values = ToolArgs.asStringList(value);
        if (values.isEmpty()) return EnumSet.allOf(BusinessResultType.class);
        EnumSet<BusinessResultType> result = EnumSet.noneOf(BusinessResultType.class);
        for (String item : values) {
            try {
                result.add(BusinessResultType.valueOf(item.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Tool arguments are schema-constrained; ignore an unknown value defensively.
            }
        }
        return result.isEmpty() ? EnumSet.allOf(BusinessResultType.class) : result;
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
