package org.example.ai.business.index;

import java.time.Instant;

public record BusinessIndexStatus(
        boolean running,
        long companiesIndexed,
        Instant lastRunAt,
        String lastStatus,
        String notes) {
}
