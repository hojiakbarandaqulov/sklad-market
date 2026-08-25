package org.example.repository;

import jakarta.persistence.LockModeType;
import org.example.entity.ActionDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ActionDraftRepository extends JpaRepository<ActionDraft, UUID> {

    Optional<ActionDraft> findByIdAndUserSub(UUID id, String userSub);

    /** Serializes confirm/cancel transitions for one owned draft until the surrounding transaction commits. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select draft from ActionDraft draft where draft.id = :id and draft.userSub = :userSub")
    Optional<ActionDraft> findLockedByIdAndUserSub(@Param("id") UUID id, @Param("userSub") String userSub);
}
