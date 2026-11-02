package org.example.service.impl;

import org.example.service.ServiceTokenService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.example.exp.OrderException;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.*;

@Service
public class ServiceTokenServiceImpl implements ServiceTokenService {
    private final RestClient client;
    private final String tokenUrl, clientId, secret;
    private final Clock clock;
    private String token;
    private Instant validUntil = Instant.EPOCH;
    public ServiceTokenServiceImpl(@Value("${order.service-auth.token-url}") String tokenUrl,
            @Value("${order.service-auth.client-id}") String clientId,
            @Value("${order.service-auth.client-secret}") String secret, Clock clock) {
        this.tokenUrl = tokenUrl; this.clientId = clientId; this.secret = secret; this.clock = clock;
        var factory = new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(3000); factory.setReadTimeout(8000);
        client = RestClient.builder().requestFactory(factory).build();
    }
    public synchronized String token() {
        if (token != null && clock.instant().isBefore(validUntil)) return token;
        if (secret.isBlank()) throw new OrderException(503, "Order service-account sozlanmagan");
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials"); form.add("client_id", clientId); form.add("client_secret", secret);
        try {
            Token response = client.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Token.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank() || response.expiresIn() <= 0)
                throw OrderException.unavailable();
            token = response.accessToken(); validUntil = clock.instant().plusSeconds(Math.max(1, response.expiresIn() - 20));
            return token;
        } catch (org.springframework.web.client.RestClientException e) { throw OrderException.unavailable(); }
    }
    record Token(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) { }
}
