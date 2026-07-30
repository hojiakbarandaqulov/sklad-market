package org.example.service.impl;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.example.dto.chat.WsTokenResponse;
import org.example.exp.AppBadException;
import org.example.service.ResourceBundleService;
import org.example.service.ChatWebSocketTokenService;
import org.example.utils.SpringSecurityUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class ChatWebSocketTokenServiceImpl implements ChatWebSocketTokenService {

    private final ResourceBundleService messageService;

    @Value("${chat.websocket.token-secret}")
    private String tokenSecret;

    @Value("${chat.websocket.token-expiration-second}")
    private long expiresInMinutes;

    @Override
    public WsTokenResponse issueToken() {
        Long userId = SpringSecurityUtil.getProfileId();
        if (userId == null) {
            throw new AppBadException(messageService.getMessage("auth.unauthorized"));
        }

        Instant now = Instant.now();
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("purpose", "chat-ws")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expiresInMinutes)))
                .signWith(getSecretKey())
                .compact();

        return new WsTokenResponse(token, expiresInMinutes);
    }

    @Override
    public Long parseToken(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!"chat-ws".equals(claims.get("purpose"))) {
                throw new AppBadException(messageService.getMessage("chat.websocket.token.invalid"));
            }

            return Long.valueOf(claims.getSubject());
        } catch (Exception e) {
            throw new AppBadException(messageService.getMessage("chat.websocket.token.invalid"));
        }
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(tokenSecret.getBytes(StandardCharsets.UTF_8));
    }
}
