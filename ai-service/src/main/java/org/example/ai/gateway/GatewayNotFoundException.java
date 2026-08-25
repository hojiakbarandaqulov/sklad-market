package org.example.ai.gateway;

/**
 * Any 4xx from the gateway (PLAN.md §7 item 10: {@code product.not.found}/{@code company.not.found}
 * map to HTTP 400, never 404; category-by-slug 4xx's with a plain-text body on unknown/inactive
 * slugs — re-verified live against skladmarket.uz for Phase 2). Callers must treat every 4xx
 * uniformly as "not found or invalid", never parse the body for a specific shape.
 */
public class GatewayNotFoundException extends RuntimeException {
    public GatewayNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
