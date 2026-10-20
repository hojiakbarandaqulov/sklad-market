package org.example.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;

/** Only allowlisted, non-personal OAuth diagnostics may reach application logs. */
record GoogleTokenExchangeFailure(int status, String oauthError, String reason) {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> ERRORS = Set.of(
            "invalid_request", "invalid_token", "invalid_issuer", "access_denied",
            "not_allowed", "invalid_client", "unauthorized_client", "invalid_grant",
            "invalid_scope", "unsupported_grant_type", "unsupported_token_type",
            "server_error", "temporarily_unavailable");
    private static final Map<String, String> REASONS = Map.ofEntries(
            Map.entry("Client not allowed to exchange", "CLIENT_NOT_ALLOWED"),
            Map.entry("Invalid subject_issuer parameter", "INVALID_ISSUER"),
            Map.entry("User already exists", "USER_NOT_LINKED"),
            Map.entry("Invalid client credentials", "INVALID_CLIENT_CREDENTIALS"),
            Map.entry("Invalid client or Invalid client credentials", "INVALID_CLIENT_CREDENTIALS"),
            Map.entry("Client not found", "CLIENT_NOT_FOUND"),
            Map.entry("Client not enabled", "CLIENT_DISABLED"),
            Map.entry("Unsupported grant_type", "TOKEN_EXCHANGE_UNSUPPORTED"),
            Map.entry("Invalid token type, must be access token", "INVALID_TOKEN_TYPE"),
            Map.entry("Invalid token", "INVALID_TOKEN"),
            Map.entry("Invalid Token", "INVALID_TOKEN"),
            Map.entry("requested_token_type unsupported", "UNSUPPORTED_REQUESTED_TOKEN_TYPE"),
            Map.entry("Issuer does not support token exchange", "PROVIDER_EXCHANGE_UNSUPPORTED"));

    static GoogleTokenExchangeFailure from(int status, String body) {
        String error = "unknown";
        String reason = "UNCLASSIFIED";
        // An HTML proxy error or an oversized response must not hide the original failure.
        if (body != null && body.length() <= 16384) {
            try {
                JsonNode data = JSON.readTree(body);
                if (data != null && data.isObject()) {
                    String code = data.path("error").asText("");
                    error = ERRORS.contains(code) ? code : "unknown";
                    reason = REASONS.getOrDefault(data.path("error_description").asText(""), "UNCLASSIFIED");
                }
            } catch (java.io.IOException ignored) {
                // Do not log raw content, parsing errors, tokens or account-link URLs.
            }
        }
        return new GoogleTokenExchangeFailure(status, error, reason);
    }
}
