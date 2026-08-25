package org.example.ai.business.tool;

import org.example.ai.business.dto.SupplierRecommendationResponse;
import org.example.ai.business.service.SupplierRecommendationService;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.CategoryResolver;
import org.example.ai.tool.ToolArgs;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RecommendSuppliersTool implements AgentTool {

    public static final String NAME = "recommend_suppliers";

    private final SupplierRecommendationService service;
    private final CategoryResolver categoryResolver;

    public RecommendSuppliersTool(SupplierRecommendationService service, CategoryResolver categoryResolver) {
        this.service = service;
        this.categoryResolver = categoryResolver;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Recommend suppliers indexed as verified to a buyer. An explicit need is preferred; otherwise "
                + "the ranking uses only the buyer's own cart, favorites, and leads ephemerally. Returns grounded "
                + "reasons, index freshness, and optional public company contacts. Current verification, catalog "
                + "availability, and commercial outcomes are not guaranteed, and the tool never contacts anyone.";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("need", Map.of("type", "STRING", "description", "Optional description of the buyer's need."));
        properties.put("categorySlug", Map.of("type", "STRING", "description", "Optional category slug."));
        properties.put("regionId", Map.of("type", "INTEGER", "description", "Optional desired region id."));
        properties.put("limit", Map.of("type", "INTEGER", "description", "Maximum suppliers, default 8, max 20."));
        properties.put("presentation", Map.of("type", "STRING", "enum", List.of("CARDS", "PLAIN_TEXT"),
                "description", "Use PLAIN_TEXT only when the user explicitly asks for no cards."));
        return Map.of("type", "OBJECT", "properties", properties, "required", List.of());
    }

    @Override
    public Set<String> allowedRoles() {
        return Set.of("BUYER");
    }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        String categorySlug = ToolArgs.asString(args.get("categorySlug"));
        Long categoryId = null;
        if (categorySlug != null) {
            categoryId = categoryResolver.resolve(categorySlug, context).orElse(null);
            if (categoryId == null) return ToolResult.notFound("Category not found: " + categorySlug);
        }
        try {
            SupplierRecommendationResponse response = service.recommend(
                    ToolArgs.asString(args.get("need")), categoryId, asLong(args.get("regionId")),
                    null, null,
                    args.get("limit") instanceof Number number ? number.intValue() : null, context);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("kind", "supplier_recommendations");
            data.put("presentation", ToolArgs.presentation(args.get("presentation")));
            data.put("personalizationSource", response.personalizationSource());
            data.put("count", response.count());
            data.put("items", response.items());
            data.put("scoreMeaning", response.scoreMeaning());
            data.put("disclaimer", response.disclaimer());
            data.put("indexFreshness", response.indexFreshness());
            return ToolResult.ok(data);
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage(), 400);
        } catch (RuntimeException e) {
            return ToolResult.error("Supplier recommendations are temporarily unavailable", null);
        }
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }
}
