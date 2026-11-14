package org.example.service.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.exp.AppServiceUnavailableException;
import org.example.exp.OrderException;
import org.example.service.ServiceTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;


@Service
@ConditionalOnProperty(prefix = "company.service-auth", name = "token-url")
public class ServiceTokenServiceImpl implements ServiceTokenService {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final int TOKEN_REFRESH_MARGIN_SECONDS = 20;

    private final RestClient restClient;
    private final String tokenUrl;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private String cachedAccessToken;
    private Instant tokenValidUntil = Instant.EPOCH;

    public ServiceTokenServiceImpl(
            @Value("${company.service-auth.token-url}") String tokenUrl,
            @Value("${company.service-auth.client-id}") String clientId,
            @Value("${company.service-auth.client-secret}") String clientSecret,
            Clock clock
    ) {
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
        this.restClient = createRestClient();
    }

    @Override
    public synchronized String token() {
        // Bir nechta parallel so'rov bir vaqtda yangi token so'ramasligi uchun synchronized ishlatilgan.
        if (cachedAccessToken != null && clock.instant().isBefore(tokenValidUntil)) {
            return cachedAccessToken;
        }

        if (clientSecret.isBlank()) {
            throw new AppServiceUnavailableException("order.service.credentials",
                    "operation=keycloak.token failure=missing_client_secret");
        }

        TokenResponse response = requestNewToken();
        validateTokenResponse(response);

        // Tarmoqdagi kechikishni hisobga olib, tokenni tugashidan biroz oldin yangilaymiz.
        long cacheDurationSeconds = Math.max(1, response.expiresIn() - TOKEN_REFRESH_MARGIN_SECONDS);
        cachedAccessToken = response.accessToken();
        tokenValidUntil = clock.instant().plusSeconds(cacheDurationSeconds);

        return cachedAccessToken;
    }

    private TokenResponse requestNewToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        try {
            return restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientResponseException exception) {
            throw OrderException.unavailable("operation=keycloak.token upstream_status="
                    + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw OrderException.unavailable("operation=keycloak.token failure="
                    + exception.getClass().getSimpleName());
        }
    }

    private void validateTokenResponse(TokenResponse response) {
        if (response == null || response.accessToken() == null
                || response.accessToken().isBlank() || response.expiresIn() <= 0) {
            throw OrderException.unavailable("operation=keycloak.token failure=invalid_response");
        }
    }

    private RestClient createRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {
    }
}
