package org.example.repository;

import org.example.entity.EmailHistory;
import org.example.enums.EmailType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailHistoryRepository extends JpaRepository<EmailHistory,Long> {

    Long countByEmailAndCreatedDateBetween(String email, LocalDateTime localDateTime, LocalDateTime now);

    Optional<EmailHistory> findTop1ByEmailOrderByCreatedDateDesc(String email);

    @Transactional
    @Modifying
    @Query("update EmailHistory set attemptCount = coalesce(attemptCount,0)+1 where  id=?1")
    void updateAttemptCount(Long id);

    Optional<EmailHistory> findTop1ByEmailAndEmailTypeOrderByCreatedDateDesc(String email, EmailType emailType);
}
