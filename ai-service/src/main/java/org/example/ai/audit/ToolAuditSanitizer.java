package org.example.ai.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/** Removes free text and PII-capable arguments before any durable audit/message persistence. */
public final class ToolAuditSanitizer {

    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> SENSITIVE_PARTS = Set.of(
            "contact", "phone", "email", "address", "message", "comment", "description",
            "need", "query", "content", "reply", "website", "url", "text", "name");
    private static final Set<String> NON_DURABLE_PUBLIC_CONTACT_KEYS = Set.of(
            "contact", "publiccontact", "businesscontact", "phoneprimary", "phonesecondary",
            "contactphone", "contactemail", "contactname", "website", "address");

    private ToolAuditSanitizer() {
    }

    public static Map<String, Object> sanitize(Map<String, Object> arguments) {
        return sanitize(null, arguments);
    }

    public static Map<String, Object> sanitize(String toolName, Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) return Map.of();
        Map<String, Object> sanitized = new LinkedHashMap<>();
        arguments.forEach((key, value) -> sanitized.put(key,
                sensitiveKey(key) || toolSpecificFreeText(toolName, key) ? REDACTED : sanitizeValue(value)));
        return Collections.unmodifiableMap(sanitized);
    }

    /**
     * Validation failed or the tool was unknown: neither model-controlled keys nor values are
     * schema-trusted enough to store. Keep only fixed structural metadata for abuse diagnostics.
     */
    public static Map<String, Object> sanitizeRejected(
            String toolName, Map<String, Object> arguments) {
        int count = arguments == null ? 0 : arguments.size();
        return Map.of("redacted", true, "argumentCount", count);
    }

    /**
     * Converts structured tool output to JSON-shaped values and strips live-hydrated public contact
     * snapshots before durable conversation persistence. The same result may still be sent live by
     * SSE; a later history load must verify contacts from the company profile again.
     */
    public static Map<String, Object> sanitizeResultSetForPersistence(
            ObjectMapper objectMapper, Map<String, Object> resultSet) {
        if (resultSet == null || resultSet.isEmpty()) return Map.of();
        Map<String, Object> jsonShape = objectMapper.convertValue(
                resultSet, new TypeReference<Map<String, Object>>() {});
        return stripPublicContacts(jsonShape);
    }

    private static Map<String, Object> stripPublicContacts(Map<?, ?> source) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String name = String.valueOf(key);
            String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
            if ("contactstatus".equals(normalized)) {
                sanitized.put(name, "NOT_CHECKED");
            } else if ("contactavailable".equals(normalized)) {
                sanitized.put(name, false);
            } else if (!NON_DURABLE_PUBLIC_CONTACT_KEYS.contains(normalized)) {
                sanitized.put(name, stripPublicContactsValue(value));
            }
        });
        return Collections.unmodifiableMap(sanitized);
    }

    private static Object stripPublicContactsValue(Object value) {
        if (value instanceof Map<?, ?> map) return stripPublicContacts(map);
        if (value instanceof List<?> list) {
            return list.stream().map(ToolAuditSanitizer::stripPublicContactsValue).toList();
        }
        return value;
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof CharSequence || value instanceof Character) {
            // Even a schema-valid slug/enum field is still model-controlled and can contain copied
            // user text. Durable audit needs shape/counts, not the original string value.
            return REDACTED;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                String name = String.valueOf(key);
                nested.put(name, sensitiveKey(name) ? REDACTED : sanitizeValue(nestedValue));
            });
            return Collections.unmodifiableMap(nested);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ToolAuditSanitizer::sanitizeValue).toList();
        }
        return value;
    }

    private static boolean sensitiveKey(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return SENSITIVE_PARTS.stream().anyMatch(normalized::contains);
    }

    private static boolean toolSpecificFreeText(String toolName, String key) {
        if (toolName == null || key == null) return false;
        return switch (toolName) {
            case "draft_buying_intent", "search_buying_intents" ->
                    Set.of("category", "region", "quantityUnit").contains(key);
            default -> false;
        };
    }
}
