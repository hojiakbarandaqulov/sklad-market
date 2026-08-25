package org.example.ai.guardrail;

import org.example.repository.UsageLedgerRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UsageLedgerServiceTest {

    private final UsageLedgerRepository repository = mock(UsageLedgerRepository.class);
    private final UsageLedgerService service = new UsageLedgerService(repository);

    @Test
    void embeddingRequestsConsumeConservativeDailyBudgetUnits() {
        service.recordEmbeddingRequest("buyer", "12345678");
        service.recordEmbeddingRequest("cold-start", null);

        verify(repository).recordUsage(eq("buyer"), any(LocalDate.class), eq(2L), eq(0L));
        verify(repository).recordUsage(eq("cold-start"), any(LocalDate.class), eq(32L), eq(0L));
    }
}
