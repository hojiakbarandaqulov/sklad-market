package org.example.ai.business.dto;

import java.time.Instant;

/**
 * Effective freshness of the local indexes used to build a result set. {@code asOf} is the
 * oldest successful refresh among the required indexes; null means freshness could not be
 * established. A stale result can still be useful, but verification/catalog claims are only
 * historical as of this timestamp and must not be presented as live guarantees.
 */
public record BusinessIndexFreshness(
        Instant asOf,
        boolean stale,
        String sourceStatus,
        String note) {
}
