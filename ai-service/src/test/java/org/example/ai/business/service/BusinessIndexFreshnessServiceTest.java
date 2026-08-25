package org.example.ai.business.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessIndexFreshnessServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T06:00:00Z");

    @Test
    void usesOldestRequiredSuccessfulRunAsEffectiveAsOf() {
        var freshness = BusinessIndexFreshnessService.evaluate(List.of(
                new BusinessIndexFreshnessService.SourceState(
                        "product", "SUCCESS", NOW.minus(Duration.ofMinutes(15)), false),
                new BusinessIndexFreshnessService.SourceState(
                        "company", "SUCCESS", NOW.minus(Duration.ofMinutes(25)), false)),
                NOW, Duration.ofMinutes(90));

        assertThat(freshness.stale()).isFalse();
        assertThat(freshness.asOf()).isEqualTo(NOW.minus(Duration.ofMinutes(25)));
        assertThat(freshness.sourceStatus()).isEqualTo("product=SUCCESS;company=SUCCESS");
    }

    @Test
    void marksLatestPartialOrOldSuccessfulRunAsStale() {
        var freshness = BusinessIndexFreshnessService.evaluate(List.of(
                new BusinessIndexFreshnessService.SourceState(
                        "company", "PARTIAL", NOW.minus(Duration.ofHours(2)), false)),
                NOW, Duration.ofMinutes(90));

        assertThat(freshness.stale()).isTrue();
        assertThat(freshness.note()).contains("out of date");
    }

    @Test
    void omitsAsOfWhenAnyRequiredSourceHasNeverCompleted() {
        var freshness = BusinessIndexFreshnessService.evaluate(List.of(
                new BusinessIndexFreshnessService.SourceState(
                        "product", "SUCCESS", NOW.minus(Duration.ofMinutes(10)), false),
                new BusinessIndexFreshnessService.SourceState(
                        "company", "NEVER_RUN", null, false)),
                NOW, Duration.ofMinutes(90));

        assertThat(freshness.stale()).isTrue();
        assertThat(freshness.asOf()).isNull();
    }
}
