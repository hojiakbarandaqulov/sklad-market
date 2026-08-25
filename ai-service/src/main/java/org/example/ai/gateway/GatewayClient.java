package org.example.ai.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

/**
 * Thin GET-only HTTP client for calling the platform's public/user-scoped REST APIs through the
 * gateway with the caller's own JWT (PLAN.md §4.2 item 1 — tools are read-only, so only GET is
 * exposed). Every 4xx is mapped uniformly to {@link GatewayNotFoundException} without inspecting
 * the body (PLAN.md §7 item 10 — the error shape differs per service: some are JSON, some are
 * plain text; re-verified live for Phase 2 that category/product/company services all still
 * return 4xx, never 404, for "not found").
 */
@Component
public class GatewayClient {

    private final RestClient restClient;

    public GatewayClient(
            @Value("${ai.gateway.base-url}") String baseUrl,
            @Value("${ai.limits.request-timeout-seconds:60}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Duration.ofSeconds(timeoutSeconds).toMillis();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    /**
     * @param pathTemplate a URI template, e.g. {@code "/api/v1/products/{slug}"} — path variables
     *                     are expanded (and encoded) via {@code uriVariables}, in order.
     */
    public <T> T get(
            String pathTemplate,
            MultiValueMap<String, String> queryParams,
            String bearerToken,
            String acceptLanguage,
            ParameterizedTypeReference<T> responseType,
            Object... uriVariables) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(pathTemplate);
                        if (queryParams != null && !queryParams.isEmpty()) {
                            uriBuilder.queryParams(queryParams);
                        }
                        return uriBuilder.build(uriVariables);
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage)
                    .retrieve()
                    .body(responseType);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new GatewayNotFoundException("Gateway rejected " + pathTemplate + ": " + e.getStatusCode(), e);
            }
            throw new GatewayUnavailableException("Gateway error on " + pathTemplate + ": " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new GatewayUnavailableException("Gateway call failed: " + pathTemplate, e);
        }
    }

    /**
     * Raw-bytes GET, for binary endpoints such as file-service's {@code GET /api/v1/attach/open/{id}}
     * (PLAN.md Phase 6: fetching an already-uploaded seller image for the vision suggest-listing
     * call). That endpoint is public/{@code permitAll} on file-service, but the Bearer token is
     * still forwarded for consistency with every other gateway call.
     */
    public GatewayImageBytes getBytes(String pathTemplate, String bearerToken, String acceptLanguage, Object... uriVariables) {
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri(pathTemplate, uriVariables)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage)
                    .retrieve()
                    .toEntity(byte[].class);
            MediaType contentType = response.getHeaders().getContentType();
            return new GatewayImageBytes(response.getBody(), contentType == null ? null : contentType.toString());
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new GatewayNotFoundException("Gateway rejected " + pathTemplate + ": " + e.getStatusCode(), e);
            }
            throw new GatewayUnavailableException("Gateway error on " + pathTemplate + ": " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            throw new GatewayUnavailableException("Gateway call failed: " + pathTemplate, e);
        }
    }
}
