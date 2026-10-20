package org.example.service.impl;

import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.dto.TokenResponseDTO;
import org.example.dto.auth.GoogleLoginRequest;
import org.example.entity.Users;
import org.example.exp.AppBadException;
import org.example.service.GoogleLoginService;
import org.example.service.GoogleProfileService;
import org.example.service.KeycloakService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;


@Service
public class GoogleLoginServiceImpl implements GoogleLoginService {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginServiceImpl.class);

    private final RestTemplate restTemplate;
    @Qualifier("googleIdTokenDecoder")
    private final JwtDecoder googleDecoder;
    @Qualifier("googleIdTokenDecoder")
    private final JwtDecoder keycloakDecoder;
    private final GoogleProfileService googleProfileService;
    private final KeycloakService keycloakService;

    @Value("${keycloak.token-url}")
    private String tokenUrl;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    public GoogleLoginServiceImpl(
            RestTemplate restTemplate,
            @Qualifier("googleIdTokenDecoder") JwtDecoder googleDecoder,
            @Qualifier("keycloakLoginTokenDecoder") JwtDecoder keycloakDecoder,
            GoogleProfileService googleProfileService,
            KeycloakService keycloakService) {

        this.restTemplate = restTemplate;
        this.googleDecoder = googleDecoder;
        this.keycloakDecoder = keycloakDecoder;
        this.googleProfileService = googleProfileService;
        this.keycloakService = keycloakService;
    }

    @Override
    public TokenResponseDTO login(GoogleLoginRequest request) {
        Jwt googleToken;

        try {
            // Google tokenining imzosi, muddati va client'i tekshiriladi.
            googleToken = googleDecoder.decode(request.idToken());
        } catch (JwtException e) {
            throw new BadCredentialsException(
                    "Google tokeni yaroqsiz yoki muddati tugagan");
        }

        String email = googleToken.getClaimAsString("email");

        if (googleToken.getSubject() == null
                || googleToken.getSubject().isBlank()
                || email == null
                || email.isBlank()
                || !Boolean.TRUE.equals(
                googleToken.getClaimAsBoolean("email_verified"))) {

            throw new BadCredentialsException(
                    "Google akkauntida tasdiqlangan email kerak");
        }

        // Google ID tokeni Keycloak tokenlariga almashtiriladi.
        TokenResponseDTO initialTokens =
                exchangeGoogleToken(request.idToken());

        // Bu sub Google sub'i emas, Keycloak user ID hisoblanadi.
        Jwt keycloakToken =
                keycloakDecoder.decode(initialTokens.getAccessToken());

        String keycloakId = keycloakToken.getSubject();

        // User bazada topiladi yoki birinchi marta yaratiladi.
        // Bu alohida service transaction'i tugagach qaytadi.
        Users user = googleProfileService.prepare(
                googleToken, keycloakId);

        // Mavjud SELLER yoki ADMIN roli BUYER'ga almashtirilmaydi.
        keycloakService.assignRoleToUser(
                keycloakId, user.getRole());

        // Sizdagi mavjud metod profileId atributini yozadi.
        // Uning implementation'ida password parametri ishlatilmaydi.
        keycloakService.addProfileIdAttribute(
                keycloakId,
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getUsername(),
                null);

        // Birinchi token profileId yozilishidan oldin olingan.
        // Shu sabab yangi claim va rollar bilan qayta token olamiz.
        TokenResponseDTO finalTokens = keycloakService.refreshToken(
                initialTokens.getRefreshToken());

        Jwt finalJwt =
                keycloakDecoder.decode(finalTokens.getAccessToken());

        // Mapper noto'g'ri bo'lsa ishlamaydigan tokenni qaytarmaymiz.
        Object profileId = finalJwt.getClaim("profileId");

        if (profileId == null
                || !user.getId().toString()
                .equals(String.valueOf(profileId))) {
            throw new IllegalStateException(
                    "Keycloak profileId mapperini sozlash kerak");
        }

        return finalTokens;
    }

    private TokenResponseDTO exchangeGoogleToken(String idToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                org.springframework.http.MediaType.valueOf(MediaType.APPLICATION_FORM_URLENCODED));

        MultiValueMap<String, String> form =
                new LinkedMultiValueMap<>();

        // Bu Google client'i emas, auth-service'ning Keycloak client'i.
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        form.add("grant_type",
                "urn:ietf:params:oauth:grant-type:token-exchange");

        // Google ID tokeni JWT sifatida yuboriladi.
        form.add("subject_token", idToken);
        form.add("subject_token_type",
                "urn:ietf:params:oauth:token-type:jwt");

        // Yuqorida yaratilgan generic OIDC provider alias'i.
        form.add("subject_issuer", "google-login");

        // Access token bilan birga refresh token ham so'raymiz.
        form.add("requested_token_type",
                "urn:ietf:params:oauth:token-type:refresh_token");

        try {
            TokenResponseDTO tokens = restTemplate.postForObject(
                    tokenUrl,
                    new HttpEntity<>(form, headers),
                    TokenResponseDTO.class);

            if (tokens == null
                    || tokens.getAccessToken() == null
                    || tokens.getRefreshToken() == null) {
                throw new IllegalStateException(
                        "Keycloak login tokenlarini qaytarmadi");
            }

            return tokens;
        } catch (HttpClientErrorException e) {
            logExchangeFailure(e);
            throw new AppBadException(
                    "Google akkauntini bog‘lash yoki "
                            + "Keycloak token-exchange sozlamalarini tekshiring");
        } catch (HttpServerErrorException e) {
            logExchangeFailure(e);
            throw new IllegalStateException("Keycloak bilan bog'lanib bo'lmadi");
        } catch (RestClientException e) {
            // Never log the exception message/body: it may contain credentials.
            log.warn("GOOGLE_TOKEN_EXCHANGE_UNAVAILABLE client_id={} issuer=google-login exception={}",
                    clientId, e.getClass().getSimpleName());
            throw new IllegalStateException(
                    "Keycloak bilan bog‘lanib bo‘lmadi");
        }
    }

    private void logExchangeFailure(org.springframework.web.client.HttpStatusCodeException exception) {
        GoogleTokenExchangeFailure failure = GoogleTokenExchangeFailure.from(
                exception.getStatusCode().value(), exception.getResponseBodyAsString());
        log.warn("GOOGLE_TOKEN_EXCHANGE_REJECTED client_id={} issuer=google-login status={} oauth_error={} reason={}",
                clientId, failure.status(), failure.oauthError(), failure.reason());
    }
}
