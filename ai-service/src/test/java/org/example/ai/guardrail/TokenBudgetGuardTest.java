package org.example.ai.guardrail;

import org.example.entity.UsageLedger;
import org.example.repository.UsageLedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBudgetGuardTest {

    @Mock
    private UsageLedgerRepository usageLedgerRepository;
    @Mock
    private AiChatRateLimitService chatLimitService;

    @Test
    void noUsageYetToday_fullBudgetRemaining() {
        TokenBudgetGuard guard = new TokenBudgetGuard(usageLedgerRepository, chatLimitService);
        when(chatLimitService.dailyTokenBudgetFor("user-1")).thenReturn(1000L);
        when(usageLedgerRepository.findByUserSubAndDay(eq("user-1"), any(LocalDate.class))).thenReturn(Optional.empty());

        assertThat(guard.hasRemainingBudget("user-1")).isTrue();
        assertThat(guard.remaining("user-1")).isEqualTo(1000L);
    }

    @Test
    void usageAtOrAboveBudget_noRemainingBudget() {
        TokenBudgetGuard guard = new TokenBudgetGuard(usageLedgerRepository, chatLimitService);
        when(chatLimitService.dailyTokenBudgetFor("user-1")).thenReturn(1000L);
        UsageLedger entry = new UsageLedger();
        entry.setTokensIn(600L);
        entry.setTokensOut(500L);
        when(usageLedgerRepository.findByUserSubAndDay(eq("user-1"), any(LocalDate.class))).thenReturn(Optional.of(entry));

        assertThat(guard.hasRemainingBudget("user-1")).isFalse();
        assertThat(guard.remaining("user-1")).isEqualTo(0L);
    }

    @Test
    void usageBelowBudget_returnsExactRemainder() {
        TokenBudgetGuard guard = new TokenBudgetGuard(usageLedgerRepository, chatLimitService);
        when(chatLimitService.dailyTokenBudgetFor("user-1")).thenReturn(1000L);
        UsageLedger entry = new UsageLedger();
        entry.setTokensIn(300L);
        entry.setTokensOut(200L);
        when(usageLedgerRepository.findByUserSubAndDay(eq("user-1"), any(LocalDate.class))).thenReturn(Optional.of(entry));

        assertThat(guard.remaining("user-1")).isEqualTo(500L);
    }
}
