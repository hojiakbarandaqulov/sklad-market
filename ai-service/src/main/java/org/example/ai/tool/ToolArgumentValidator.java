package org.example.ai.tool;

import java.util.List;
import java.util.Map;

/**
 * Validates model-supplied tool arguments against the same JSON-Schema-shaped map used to declare
 * the tool to the model (PLAN.md §4.2 item 5: "model-supplied args schema-validated and
 * enum-checked before any HTTP call"). The model is never trusted to have honored its own schema.
 */
public final class ToolArgumentValidator {

    private static final int MAX_NESTING_DEPTH = 16;

    private ToolArgumentValidator() {
    }

    public static void validate(Map<String, Object> schema, Map<String, Object> args) {
        if (args == null) {
            throw new ToolArgumentException("Missing arguments");
        }
        validateValue("arguments", args, schema == null ? Map.of() : schema, 0);
    }

    private static void validateValue(
            String key, Object value, Map<String, Object> propertySchema, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            throw new ToolArgumentException("Argument '" + key + "' is nested too deeply");
        }
        if (value == null) {
            return;
        }
        Object type = propertySchema.get("type");
        if (type instanceof String typeName && !matchesType(typeName, value)) {
            throw new ToolArgumentException("Argument '" + key + "' must be of type " + typeName);
        }
        Object enumValues = propertySchema.get("enum");
        if (enumValues instanceof List<?> allowed && !allowed.isEmpty()) {
            if (!(value instanceof String) || !allowed.contains(value)) {
                throw new ToolArgumentException("Argument '" + key + "' must be one of " + allowed);
            }
        }
        if (value instanceof Map<?, ?> map) {
            validateObject(key, map, propertySchema, depth);
        } else if (value instanceof List<?> list) {
            validateArray(key, list, propertySchema, depth);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateObject(
            String path, Map<?, ?> value, Map<String, Object> schema, int depth) {
        Object rawProperties = schema.get("properties");
        Map<String, Object> properties = rawProperties instanceof Map<?, ?>
                ? (Map<String, Object>) rawProperties
                : Map.of();
        Object rawRequired = schema.get("required");
        List<?> required = rawRequired instanceof List<?> list ? list : List.of();
        for (Object requiredKey : required) {
            String name = String.valueOf(requiredKey);
            if (value.get(name) == null) {
                throw new ToolArgumentException("Missing required argument: " + path + "." + name);
            }
        }
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (!(entry.getKey() instanceof String name)) {
                throw new ToolArgumentException("Argument '" + path + "' has a non-string object key");
            }
            Object rawChildSchema = properties.get(name);
            if (!(rawChildSchema instanceof Map<?, ?> childSchema)) {
                throw new ToolArgumentException("Unknown argument: " + path + "." + name);
            }
            validateValue(path + "." + name, entry.getValue(), (Map<String, Object>) childSchema, depth + 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateArray(
            String path, List<?> value, Map<String, Object> schema, int depth) {
        Object rawItems = schema.get("items");
        if (!(rawItems instanceof Map<?, ?> itemSchema)) {
            if (!value.isEmpty()) {
                throw new ToolArgumentException("Argument '" + path + "' has no item schema");
            }
            return;
        }
        for (int index = 0; index < value.size(); index++) {
            validateValue(path + "[" + index + "]", value.get(index),
                    (Map<String, Object>) itemSchema, depth + 1);
        }
    }

    private static boolean matchesType(String typeName, Object value) {
        return switch (typeName) {
            case "STRING" -> value instanceof String;
            case "NUMBER" -> value instanceof Number number && Double.isFinite(number.doubleValue());
            case "INTEGER" -> value instanceof Number number && isIntegral(number);
            case "BOOLEAN" -> value instanceof Boolean;
            case "ARRAY" -> value instanceof List;
            case "OBJECT" -> value instanceof Map;
            default -> true;
        };
    }

    private static boolean isIntegral(Number number) {
        double d = number.doubleValue();
        return !Double.isInfinite(d) && !Double.isNaN(d) && d == Math.floor(d);
    }
}
