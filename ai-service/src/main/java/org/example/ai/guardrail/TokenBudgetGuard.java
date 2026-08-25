package org.example.ai.guardrail;

import org.example.entity.UsageLedger;
import org.example.repository.UsageLedgerRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Enforces {@code AI_DAILY_TOKEN_BUDGET} against the persisted {@code usage_ledger}. */
@Component
public class TokenBudgetGuard {

    private final UsageLedgerRepository usageLedgerRepository;
    private final AiChatRateLimitService chatLimitService;

    public TokenBudgetGuard(
            UsageLedgerRepository usageLedgerRepository,
            AiChatRateLimitService chatLimitService) {
        this.usageLedgerRepository = usageLedgerRepository;
        this.chatLimitService = chatLimitService;
    }

    public boolean hasRemainingBudget(String userSub) {
        return remaining(userSub) > 0;
    }

    public long remaining(String userSub) {
        long used = usedToday(userSub);
        long dailyBudget = chatLimitService.dailyTokenBudgetFor(userSub);
        return Math.max(0L, dailyBudget - used);
    }

    private long usedToday(String userSub) {
        return usageLedgerRepository
                .findByUserSubAndDay(userSub, LocalDate.now())
                .map(entry -> entry.getTokensIn() + entry.getTokensOut())
                .orElse(0L);
    }
}
