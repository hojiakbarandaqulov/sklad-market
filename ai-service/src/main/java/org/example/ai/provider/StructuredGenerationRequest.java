package org.example.ai.provider;

import java.util.List;
import java.util.Map;

/**
 * A single-shot, non-streaming, optionally vision-capable generation call whose output is
 * constrained to JSON matching {@code responseSchema} (a plain JSON-Schema-shaped map, the same
 * provider-agnostic convention {@link ToolSpec#parametersSchema()} already uses — no caller outside
 * {@code org.example.ai.provider} depends on an SDK type). Used by PLAN.md Phase 6's seller
 * suggest-listing feature (category pick, then attribute-value extraction against the category's
 * real schema) — never by the streaming chat loop.
 */
public record StructuredGenerationRequest(
        String model,
        String systemInstruction,
        String userText,
        List<ImagePart> images,
        Map<String, Object> responseSchema,
        float temperature,
        int maxOutputTokens) {
}
