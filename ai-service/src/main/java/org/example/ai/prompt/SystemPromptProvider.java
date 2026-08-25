package org.example.ai.prompt;

import jakarta.annotation.PostConstruct;
import org.example.ai.LocaleNormalizer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Loads the versioned system prompt, shared marketplace guide, and per-persona fragments once,
 * then composes them per turn based on the caller's live role set (PLAN.md Phase 6:
 * "persona-aware prompts"). A BUYER-only caller never sees the seller/admin sections at all — the
 * model isn't even told those tools exist for them, on top of the registry/`@PreAuthorize` gates.
 */
@Component
public class SystemPromptProvider {

    private static final String BASE_RESOURCE = "prompts/system-agent-v5.md";
    private static final String MARKETPLACE_GUIDE_RESOURCE = "prompts/marketplace-guide-v1.md";
    private static final String SELLER_PERSONA_RESOURCE = "prompts/persona-seller-v1.md";
    private static final String ADMIN_PERSONA_RESOURCE = "prompts/persona-admin-v1.md";

    private String basePrompt;
    private String marketplaceGuidePrompt;
    private String sellerPersonaPrompt;
    private String adminPersonaPrompt;

    @PostConstruct
    void load() {
        basePrompt = readResource(BASE_RESOURCE);
        marketplaceGuidePrompt = readResource(MARKETPLACE_GUIDE_RESOURCE);
        sellerPersonaPrompt = readResource(SELLER_PERSONA_RESOURCE);
        adminPersonaPrompt = readResource(ADMIN_PERSONA_RESOURCE);
    }

    /** Appends the conversation's stored locale hint, then any persona sections the caller's roles unlock. */
    public String render(String conversationLocale, Set<String> callerRoles) {
        StringBuilder prompt = new StringBuilder(basePrompt);
        prompt.append("\n\n").append(marketplaceGuidePrompt);
        prompt.append("\n\nConversation locale (ambiguity fallback only): ").append(LocaleNormalizer.normalize(conversationLocale));

        Set<String> roles = callerRoles == null ? Set.of() : callerRoles;
        if (roles.contains("SELLER")) {
            prompt.append("\n\n").append(sellerPersonaPrompt);
        }
        if (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN")) {
            prompt.append("\n\n").append(adminPersonaPrompt);
        }
        return prompt.toString();
    }

    private String readResource(String resource) {
        try {
            return new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + resource, e);
        }
    }
}
