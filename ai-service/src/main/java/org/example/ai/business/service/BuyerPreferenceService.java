package org.example.ai.business.service;

import lombok.extern.slf4j.Slf4j;
import org.example.ai.gateway.GatewayClient;
import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteCartDto;
import org.example.ai.gateway.dto.RemoteFavoriteProductDto;
import org.example.ai.gateway.dto.RemoteLeadDto;
import org.example.ai.gateway.dto.RemotePagedResponse;
import org.example.ai.gateway.dto.RemoteSpringPage;
import org.example.ai.tool.PlatformLanguage;
import org.example.ai.tool.ToolExecutionContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds an ephemeral preference phrase from the caller's own activity; it is never persisted. */
@Slf4j
@Service
public class BuyerPreferenceService {

    private static final int MAX_TERMS = 20;
    private final GatewayClient gatewayClient;

    public BuyerPreferenceService(GatewayClient gatewayClient) {
        this.gatewayClient = gatewayClient;
    }

    public Preference resolve(String explicitNeed, ToolExecutionContext context) {
        String trimmed = explicitNeed == null ? "" : explicitNeed.trim();
        if (!trimmed.isEmpty()) {
            if (trimmed.length() > 500) throw new IllegalArgumentException("need must not exceed 500 characters");
            return new Preference(trimmed, "EXPLICIT");
        }

        Set<String> terms = new LinkedHashSet<>();
        collectCart(terms, context);
        collectFavorites(terms, context);
        collectLeads(terms, context);
        if (!terms.isEmpty()) {
            return new Preference(String.join("; ", terms.stream().limit(MAX_TERMS).toList()), "OWN_ACTIVITY");
        }
        return new Preference("verified wholesale suppliers with active product catalogs", "COLD_START");
    }

    private void collectCart(Set<String> terms, ToolExecutionContext context) {
        try {
            GatewayEnvelope<RemoteCartDto> envelope = gatewayClient.get(
                    "/api/v1/cart", null, context.bearerToken(), PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemoteCartDto>>() {});
            RemoteCartDto cart = envelope == null ? null : envelope.data();
            if (cart != null && cart.items() != null) {
                cart.items().stream().map(RemoteCartDto.RemoteCartItemDto::productName)
                        .filter(this::usable).limit(MAX_TERMS).forEach(terms::add);
            }
        } catch (GatewayNotFoundException | GatewayUnavailableException e) {
            log.debug("Buyer cart unavailable for supplier personalization: {}", e.getMessage());
        }
    }

    private void collectFavorites(Set<String> terms, ToolExecutionContext context) {
        try {
            LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("page", "1");
            params.add("perPage", "10");
            GatewayEnvelope<RemoteSpringPage<RemoteFavoriteProductDto>> envelope = gatewayClient.get(
                    "/api/v1/product-favorites", params, context.bearerToken(),
                    PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemoteSpringPage<RemoteFavoriteProductDto>>>() {});
            RemoteSpringPage<RemoteFavoriteProductDto> page = envelope == null ? null : envelope.data();
            if (page != null && page.content() != null) {
                page.content().stream().map(RemoteFavoriteProductDto::name)
                        .filter(this::usable).limit(MAX_TERMS).forEach(terms::add);
            }
        } catch (GatewayNotFoundException | GatewayUnavailableException e) {
            log.debug("Buyer favorites unavailable for supplier personalization: {}", e.getMessage());
        }
    }

    private void collectLeads(Set<String> terms, ToolExecutionContext context) {
        try {
            LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("page", "1");
            params.add("perPage", "10");
            GatewayEnvelope<RemotePagedResponse<RemoteLeadDto>> envelope = gatewayClient.get(
                    "/api/v1/leads", params, context.bearerToken(), PlatformLanguage.header(context.acceptLanguage()),
                    new ParameterizedTypeReference<GatewayEnvelope<RemotePagedResponse<RemoteLeadDto>>>() {});
            RemotePagedResponse<RemoteLeadDto> page = envelope == null ? null : envelope.data();
            List<RemoteLeadDto> leads = page == null || page.items() == null ? List.of() : page.items();
            List<String> names = new ArrayList<>();
            for (RemoteLeadDto lead : leads) {
                if (lead.items() == null) continue;
                lead.items().stream().map(RemoteLeadDto.RemoteLeadItemDto::productNameSnapshot)
                        .filter(this::usable).forEach(names::add);
            }
            names.stream().limit(MAX_TERMS).forEach(terms::add);
        } catch (GatewayNotFoundException | GatewayUnavailableException e) {
            log.debug("Buyer leads unavailable for supplier personalization: {}", e.getMessage());
        }
    }

    private boolean usable(String value) {
        return value != null && !value.isBlank() && value.length() <= 200;
    }

    public record Preference(String text, String source) {
    }
}
