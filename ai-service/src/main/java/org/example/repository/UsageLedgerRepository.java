package org.example.repository;

import org.example.entity.UsageLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

public interface UsageLedgerRepository extends JpaRepository<UsageLedger, Long> {

    Optional<UsageLedger> findByUserSubAndDay(String userSub, LocalDate day);

    @Modifying
    @Transactional
    @Query(
            value = "INSERT INTO usage_ledger (user_sub, day, tokens_in, tokens_out, request_count) "
                    + "VALUES (:userSub, :day, :tokensIn, :tokensOut, 1) "
                    + "ON CONFLICT (user_sub, day) DO UPDATE SET "
                    + "tokens_in = usage_ledger.tokens_in + EXCLUDED.tokens_in, "
                    + "tokens_out = usage_ledger.tokens_out + EXCLUDED.tokens_out, "
                    + "request_count = usage_ledger.request_count + 1",
            nativeQuery = true)
    void recordUsage(
            @Param("userSub") String userSub,
            @Param("day") LocalDate day,
            @Param("tokensIn") long tokensIn,
            @Param("tokensOut") long tokensOut);
}
