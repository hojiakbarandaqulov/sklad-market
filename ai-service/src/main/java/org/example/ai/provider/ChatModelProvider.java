package org.example.ai.provider;

import org.example.ai.error.AiChatException;

/**
 * Vendor-neutral boundary for chat generation (PLAN.md §2: "all provider access behind two
 * ai-service-owned interfaces"). No caller outside {@code org.example.ai.provider} may depend on
 * an SDK type.
 */
public interface ChatModelProvider {

    /**
     * Opens a streaming generation call. Throws {@link AiChatException} (code {@code timeout} or
     * {@code provider_error}) if the call cannot even be started; failures once the stream is open
     * surface from the returned {@link ChatStream}'s iterator instead.
     */
    ChatStream generateStream(ChatGenerationRequest request);

    /** Non-streaming generation, e.g. for future single-shot uses (titles, summaries). */
    ChatCompletionResult generate(ChatGenerationRequest request);

    /**
     * Non-streaming, optionally vision-capable, JSON-schema-constrained generation (PLAN.md Phase 6:
     * seller listing category/attribute suggestion). Never used by the chat/tool loop.
     */
    StructuredCompletionResult generateStructured(StructuredGenerationRequest request);
}
