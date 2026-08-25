package org.example.ai.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stamps every request with a correlation id in the SLF4J {@link MDC} under {@code requestId} and
 * echoes it back on the {@code X-Request-Id} response header (PLAN.md Phase 7: "structured logging
 * with request IDs"). Under the prod profile the ECS structured-logging format includes MDC fields,
 * so the id appears on every log line for that request.
 *
 * <p>Runs before the Spring Security filter chain so even 401/403 responses are correlated. An
 * inbound {@code X-Request-Id} is honored but sanitized (allowlisted charset, length-capped) so a
 * crafted header can never inject newlines/control chars into the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final int MAX_LENGTH = 64;
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._-]");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = sanitize(request.getHeader(HEADER));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH);
        }
        String cleaned = UNSAFE.matcher(trimmed).replaceAll("");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
