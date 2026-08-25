package org.example.ai.intent.service;

import org.example.ai.intent.entity.BuyingIntentStatus;
import org.example.ai.intent.repository.BuyingIntentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bounded lifecycle maintenance. Each run expires and deletes at most {@code batchSize * maxBatches}
 * rows, preventing a historical backlog from becoming an unbounded scheduled transaction.
 */
@Service
public class BuyingIntentMaintenanceService {

    private static final List<BuyingIntentStatus> ACTIVE =
            List.of(BuyingIntentStatus.DRAFT, BuyingIntentStatus.PUBLISHED);
    private static final List<BuyingIntentStatus> RETAINABLE_TERMINAL =
            List.of(BuyingIntentStatus.CLOSED, BuyingIntentStatus.EXPIRED);

    private final BuyingIntentRepository repository;
    private final Clock clock;
    private final int batchSize;
    private final int maxBatches;
    private final Duration retention;

    @Autowired
    public BuyingIntentMaintenanceService(
            BuyingIntentRepository repository,
            @Value("${ai.buying-intents.maintenance-batch-size:250}") int batchSize,
            @Value("${ai.buying-intents.maintenance-max-batches:4}") int maxBatches,
            @Value("${ai.buying-intents.retention-days:180}") int retentionDays) {
        this(repository, Clock.systemUTC(), batchSize, maxBatches, retentionDays);
    }

    BuyingIntentMaintenanceService(
            BuyingIntentRepository repository,
            Clock clock,
            int batchSize,
            int maxBatches,
            int retentionDays) {
        this.repository = repository;
        this.clock = clock;
        this.batchSize = bounded(batchSize, "maintenance batch size", 1, 1_000);
        this.maxBatches = bounded(maxBatches, "maintenance max batches", 1, 20);
        this.retention = Duration.ofDays(bounded(retentionDays, "retention days", 30, 3_650));
    }

    @Scheduled(cron = "${ai.buying-intents.maintenance-cron:0 17 * * * *}")
    @Transactional
    public MaintenanceResult runMaintenance() {
        Instant now = clock.instant();
        int expired = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            List<UUID> ids = repository.findDueIds(ACTIVE, now, PageRequest.of(0, batchSize));
            if (ids.isEmpty()) {
                break;
            }
            expired += repository.expireDueIds(ids, ACTIVE, now);
            if (ids.size() < batchSize) {
                break;
            }
        }

        int deleted = 0;
        Instant cutoff = now.minus(retention);
        for (int batch = 0; batch < maxBatches; batch++) {
            List<UUID> ids = repository.findRetentionCandidates(
                    RETAINABLE_TERMINAL, cutoff, PageRequest.of(0, batchSize));
            if (ids.isEmpty()) {
                break;
            }
            repository.deleteAllByIdInBatch(ids);
            deleted += ids.size();
            if (ids.size() < batchSize) {
                break;
            }
        }
        return new MaintenanceResult(expired, deleted, now, cutoff,
                batchSize * maxBatches);
    }

    private int bounded(int value, String label, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max);
        }
        return value;
    }

    public record MaintenanceResult(
            int expired,
            int deleted,
            Instant asOf,
            Instant retentionCutoff,
            int maximumRowsPerPhase) {
    }
}
