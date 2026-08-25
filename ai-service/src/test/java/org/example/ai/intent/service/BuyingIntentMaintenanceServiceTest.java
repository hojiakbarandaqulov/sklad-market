package org.example.ai.intent.service;

import org.example.ai.intent.entity.BuyingIntentStatus;
import org.example.ai.intent.repository.BuyingIntentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuyingIntentMaintenanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");

    @Mock
    private BuyingIntentRepository repository;

    @Test
    void maintenanceExpiresAndDeletesInBoundedBatches() {
        UUID due1 = UUID.randomUUID();
        UUID due2 = UUID.randomUUID();
        UUID due3 = UUID.randomUUID();
        UUID old1 = UUID.randomUUID();
        UUID old2 = UUID.randomUUID();
        when(repository.findDueIds(any(), eq(NOW), any(Pageable.class)))
                .thenReturn(List.of(due1, due2), List.of(due3));
        when(repository.expireDueIds(any(), any(), eq(NOW))).thenReturn(2, 1);
        when(repository.findRetentionCandidates(any(), eq(NOW.minusSeconds(180L * 86_400)), any(Pageable.class)))
                .thenReturn(List.of(old1, old2), List.of());
        BuyingIntentMaintenanceService service = new BuyingIntentMaintenanceService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), 2, 2, 180);

        var result = service.runMaintenance();

        assertThat(result.expired()).isEqualTo(3);
        assertThat(result.deleted()).isEqualTo(2);
        assertThat(result.maximumRowsPerPhase()).isEqualTo(4);
        verify(repository, times(2)).findDueIds(any(), eq(NOW), any(Pageable.class));
        verify(repository).deleteAllByIdInBatch(List.of(old1, old2));
    }

    @Test
    void maintenanceNeverProcessesMoreThanConfiguredMaximumPerPhase() {
        List<UUID> fullBatch = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(repository.findDueIds(any(), eq(NOW), any(Pageable.class))).thenReturn(fullBatch);
        when(repository.expireDueIds(any(), any(), eq(NOW))).thenReturn(2);
        when(repository.findRetentionCandidates(any(), any(), any(Pageable.class))).thenReturn(List.of());
        BuyingIntentMaintenanceService service = new BuyingIntentMaintenanceService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), 2, 2, 180);

        var result = service.runMaintenance();

        assertThat(result.expired()).isEqualTo(4);
        verify(repository, times(2)).findDueIds(any(), eq(NOW), any(Pageable.class));
        verify(repository, times(2)).expireDueIds(
                eq(fullBatch), eq(List.of(BuyingIntentStatus.DRAFT, BuyingIntentStatus.PUBLISHED)), eq(NOW));
    }
}
