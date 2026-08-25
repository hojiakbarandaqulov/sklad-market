package org.example.ai.seller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai.gateway.dto.RemoteCategoryAttributeDto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridges category-service's real {@code CategoryAttribute} schema (PLAN.md Phase 6) to (a) a
 * JSON-Schema property Gemini's structured output can be constrained to, and (b) strict server-side
 * validation of whatever the model returns — PLAN.md §4.2 item 5: never trust model output, even
 * when it was schema-constrained.
 *
 * <p>{@code optionsJson} has NO enforced shape anywhere on the platform (verified: category-service
 * stores it as an opaque {@code TEXT} column with zero parsing/validation logic anywhere in its own
 * codebase). {@link #parseOptions} best-effort-handles the two shapes that would make sense for a
 * {@code SELECT} attribute — a plain array of strings, or an array of {@code {value,label}}-ish
 * objects — and degrades to "no enum constraint" (free text) if the content matches neither, rather
 * than guessing at a contract that doesn't exist.
 */
public final class CategoryAttributeSchema {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CategoryAttributeSchema() {
    }

    /** JSON-Schema-shaped property map for one attribute, keyed by its {@code code} by the caller. */
    public static Map<String, Object> jsonSchemaProperty(RemoteCategoryAttributeDto attribute) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", schemaType(attribute.dataType()));
        if (attribute.label() != null && !attribute.label().isBlank()) {
            property.put("description", attribute.label());
        }
        if (isSelect(attribute.dataType())) {
            List<String> options = parseOptions(attribute.optionsJson());
            if (!options.isEmpty()) {
                property.put("enum", options);
            }
        }
        return property;
    }

    /** Best-effort parse of {@code optionsJson}; returns an empty list if the shape can't be recognized. */
    public static List<String> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = MAPPER.readTree(optionsJson);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (JsonNode element : node) {
                if (element.isTextual()) {
                    values.add(element.asText());
                } else if (element.isObject()) {
                    if (element.hasNonNull("value")) {
                        values.add(element.get("value").asText());
                    } else if (element.hasNonNull("label")) {
                        values.add(element.get("label").asText());
                    }
                }
            }
            return values;
        } catch (Exception malformed) {
            return List.of();
        }
    }

    /** Strict validation of a model-proposed value against the real {@code DataType} (+ {@code optionsJson} membership for SELECT). */
    public static boolean isValidValue(RemoteCategoryAttributeDto attribute, Object value, List<String> parsedOptions) {
        if (value == null) {
            return false;
        }
        String dataType = attribute.dataType() == null ? "" : attribute.dataType();
        return switch (dataType) {
            case "TEXT" -> value instanceof String text && !text.isBlank();
            case "NUMBER" -> value instanceof Number || (value instanceof String s && isNumeric(s));
            case "BOOLEAN" -> value instanceof Boolean || (value instanceof String s && isBoolean(s));
            case "SELECT" -> value instanceof String selected
                    && !parsedOptions.isEmpty()
                    && parsedOptions.stream().anyMatch(option -> option.equalsIgnoreCase(selected));
            default -> false;
        };
    }

    private static String schemaType(String dataType) {
        if (dataType == null) {
            return "STRING";
        }
        return switch (dataType) {
            case "NUMBER" -> "NUMBER";
            case "BOOLEAN" -> "BOOLEAN";
            default -> "STRING"; // TEXT and SELECT are both string-shaped on the wire
        };
    }

    private static boolean isSelect(String dataType) {
        return "SELECT".equals(dataType);
    }

    private static boolean isNumeric(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }
}
