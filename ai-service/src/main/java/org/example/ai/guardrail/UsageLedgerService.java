package org.example.ai.guardrail;

import org.example.repository.UsageLedgerRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class UsageLedgerService {

    private static final long DEFAULT_EMBEDDING_REQUEST_UNITS = 32;

    private final UsageLedgerRepository usageLedgerRepository;

    public UsageLedgerService(UsageLedgerRepository usageLedgerRepository) {
        this.usageLedgerRepository = usageLedgerRepository;
    }

    public void recordUsage(String userSub, long tokensIn, long tokensOut) {
        usageLedgerRepository.recordUsage(userSub, LocalDate.now(), tokensIn, tokensOut);
    }

    /**
     * Embedding providers do not return token accounting. Charge a conservative text-size estimate
     * (or a fixed request unit for derived/cold-start text) so direct discovery APIs cannot bypass
     * the persisted daily budget, even when a query is served from cache.
     */
    public void recordEmbeddingRequest(String userSub, String text) {
        long units = DEFAULT_EMBEDDING_REQUEST_UNITS;
        if (text != null && !text.isBlank()) {
            int codePoints = text.codePointCount(0, text.length());
            units = Math.max(1L, (codePoints + 3L) / 4L);
        }
        recordUsage(userSub, units, 0);
    }
}
