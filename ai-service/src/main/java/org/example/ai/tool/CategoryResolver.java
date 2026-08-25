package org.example.ai.tool;

import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteCategoryDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves a category slug to the numeric category id the catalog endpoints actually require.
 * Re-verified live on {@code main} for Phase 2: {@code CatalogController.getCatalog}/{@code
 * filters} still parse their {@code category} query param via {@code Long.valueOf(category)}
 * (PLAN.md §7 item 7 — still true, not a slug). A live probe of
 * {@code GET /api/v1/categories/{unknown-slug}} against skladmarket.uz on 2026-07-08 returned
 * HTTP 400 with a plain-text body ("source cannot be null") — §7 item 9's older finding of
 * {@code success:true, data:null} no longer holds; {@link org.example.ai.gateway.GatewayClient}'s
 * uniform 4xx-as-not-found handling covers both shapes regardless.
 */
@Component
public class CategoryResolver {

    private final GatewayClient gatewayClient;

    public CategoryResolver(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    public Optional<Long> resolve(String categorySlug, ToolExecutionContext context) {
        if (categorySlug == null || categorySlug.isBlank()) {
            return Optional.empty();
        }
        try {
            GatewayEnvelope<RemoteCategoryDto> envelope = gatewayClient.get(
                    "/api/v1/categories/{slug}",
                    null,
                    context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemoteCategoryDto>>() {
                    },
                    categorySlug);
            RemoteCategoryDto data = envelope == null ? null : envelope.data();
            return data == null || data.id() == null ? Optional.empty() : Optional.of(data.id());
        } catch (GatewayNotFoundException e) {
            return Optional.empty();
        }
    }
}
