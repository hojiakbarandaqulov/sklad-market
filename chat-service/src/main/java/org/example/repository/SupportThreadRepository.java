package org.example.repository;

import org.example.entity.SupportThread;
import org.example.enums.RequesterRole;
import org.example.enums.SupportThreadStatus;
import org.example.enums.SupportThreadType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.Optional;

public interface SupportThreadRepository extends JpaRepository<SupportThread, Long> {
    Optional<SupportThread> findFirstByRequesterIdAndRequesterRoleAndStatusInAndDeletedFalseOrderByIdDesc(
            Long requesterId,
            RequesterRole requesterRole,
            Collection<SupportThreadStatus> statuses
    );

    Optional<SupportThread> findFirstByRequesterIdAndRequesterRoleAndTypeAndStatusInAndDeletedFalseOrderByIdDesc(
            Long requesterId,
            RequesterRole requesterRole,
            SupportThreadType type,
            Collection<SupportThreadStatus> statuses
    );

    Optional<SupportThread> findByIdAndDeletedFalse(Long id);

    Page<SupportThread> findByDeletedFalse(Pageable pageable);

    Page<SupportThread> findByStatusAndDeletedFalse(SupportThreadStatus status, Pageable pageable);


    Page<SupportThread> findByTypeAndDeletedFalse(SupportThreadType type, Pageable pageable);

    Page<SupportThread> findByStatusAndTypeAndDeletedFalse(SupportThreadStatus status, SupportThreadType type, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SupportThread t where t.id = :id and t.deleted = false")
    Optional<SupportThread> findByIdForUpdate(@Param("id") Long id);
}
