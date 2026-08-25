package org.example.ai.business.remote;

import org.example.ai.gateway.GatewayNotFoundException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.ai.gateway.dto.GatewayEnvelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** A credential-free, deliberately short-timeout client for optional public contact enrichment. */
@Component
public class PublicCompanyContactClient {

    private final RestClient restClient;

    public PublicCompanyContactClient(
            @Value("${ai.gateway.base-url}") String baseUrl,
            @Value("${ai.business-contact.timeout-ms:1200}") int configuredTimeoutMillis) {
        int timeoutMillis = Math.max(100, Math.min(configuredTimeoutMillis, 5_000));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public RemoteCompanyDetail fetch(String slug, String acceptLanguage) {
        try {
            GatewayEnvelope<RemoteCompanyDetail> envelope = restClient.get()
                    .uri("/api/v1/companies/{slug}", slug)
                    .header(HttpHeaders.ACCEPT_LANGUAGE, acceptLanguage)
                    .retrieve()
                    .body(new ParameterizedTypeReference<GatewayEnvelope<RemoteCompanyDetail>>() {});
            return envelope == null ? null : envelope.data();
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 400 || e.getStatusCode().value() == 404) {
                throw new GatewayNotFoundException("Public company contact was not found", e);
            }
            throw new GatewayUnavailableException("Public company contact endpoint failed", e);
        } catch (RestClientException e) {
            throw new GatewayUnavailableException("Public company contact endpoint unavailable", e);
        }
    }
}
