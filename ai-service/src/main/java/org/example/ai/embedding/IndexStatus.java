package org.example.ai.embedding;

import java.time.Instant;

/** Snapshot returned by the admin status endpoint. */
public record IndexStatus(
        boolean running,
        long indexSize,
        Instant lastRunAt,
        String lastStatus,
        Integer productsIndexed,
        String notes) {
}
