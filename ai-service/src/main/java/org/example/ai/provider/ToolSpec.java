package org.example.ai.provider;

import java.util.Map;

/**
 * Vendor-neutral tool declaration handed to {@link ChatModelProvider}. {@code parametersSchema} is
 * a plain JSON-Schema-shaped map ({@code type/properties/required/enum/description}, Gemini's
 * upper-case type tokens: {@code STRING|NUMBER|INTEGER|BOOLEAN|ARRAY|OBJECT}) so it converts
 * trivially to any provider's function-calling schema without this type ever importing a vendor SDK.
 */
public record ToolSpec(String name, String description, Map<String, Object> parametersSchema) {
}
