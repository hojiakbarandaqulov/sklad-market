package org.example.ai.embedding;

import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.example.ai.gateway.dto.RemoteCategoryDto;
import org.example.ai.gateway.dto.RemoteProductListResponse;
import org.example.ai.gateway.dto.RemoteSpringPage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Read-only, <b>credential-free</b> client for the PUBLIC catalog endpoints the indexer consumes
 * (PLAN.md Phase 5: "Indexing uses PUBLIC endpoints only"). Deliberately separate from
 * {@link org.example.ai.gateway.GatewayClient} — which always forwards the caller's Bearer token —
 * because a scheduled indexer has no user context: it must NEVER attach a credential. Both endpoints
 * were verified public (HTTP 200 unauthenticated) live on skladmarket.uz, 2026-07-11.
 */
@Component
public class PublicCatalogClient {

    private final RestClient restClient;

    public PublicCatalogClient(
            @Value("${ai.gateway.base-url}") String baseUrl,
            @Value("${ai.limits.request-timeout-seconds:60}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Duration.ofSeconds(timeoutSeconds).toMillis();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    /** One page of {@code GET /api/v1/products/all} (1-based page, snake_case {@code per_page}). */
    public RemoteProductListResponse fetchProductsPage(int page, int perPage) {
        GatewayEnvelope<RemoteProductListResponse> envelope = get(
                "/api/v1/products/all?page={page}&per_page={perPage}",
                new ParameterizedTypeReference<GatewayEnvelope<RemoteProductListResponse>>() {
                },
                "UZ", page, perPage);
        return envelope == null ? null : envelope.data();
    }

    /** One page of {@code GET /api/v1/categories} (0-based, {@code size} param). Only the {@code nameXx} matching {@code acceptLanguage} is populated. */
    public RemoteSpringPage<RemoteCategoryDto> fetchCategories(int page, int size, String acceptLanguage) {
        GatewayEnvelope<RemoteSpringPage<RemoteCategoryDto>> envelope = get(
                "/api/v1/categories?page={page}&size={size}",
                new ParameterizedTypeReference<GatewayEnvelope<RemoteSpringPage<RemoteCategoryDto>>>() {
                },
                acceptLanguage, page, size);
        return envelope == null ? null : envelope.data();
    }

    private <T> T get(String uriTemplate, ParameterizedTypeReference<T> type, String acceptLanguage, Object... uriVariables) {
        try {
            return restClient.get()
                    .uri(uriTemplate, uriVariables)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage)
                    .retrieve()
                    .body(type);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new GatewayNotFoundException("Public catalog rejected " + uriTemplate + ": " + e.getStatusCode(), e);
            }
            throw new GatewayUnavailableException("Public catalog error on " + uriTemplate + ": " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new GatewayUnavailableException("Public catalog call failed: " + uriTemplate, e);
        }
    }
}
