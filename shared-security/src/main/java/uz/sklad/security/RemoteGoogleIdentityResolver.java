package uz.sklad.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class RemoteGoogleIdentityResolver implements GoogleIdentityResolver {
    private final URI endpoint;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RemoteGoogleIdentityResolver(String endpoint, ObjectMapper objectMapper) {
        this.endpoint = URI.create(endpoint);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Override
    public GoogleIdentity resolve(Jwt verifiedGoogleToken) {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + verifiedGoogleToken.getTokenValue())
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new BadCredentialsException("Google account access denied");
            }
            if (response.statusCode() != 200) {
                throw new AuthenticationServiceException("Auth service could not resolve Google identity");
            }
            return objectMapper.readValue(response.body(), GoogleIdentity.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthenticationServiceException("Google identity lookup interrupted");
        } catch (IOException e) {
            // Do not include response bodies or bearer tokens in errors/logs.
            throw new AuthenticationServiceException("Google identity service unavailable");
        }
    }
}
