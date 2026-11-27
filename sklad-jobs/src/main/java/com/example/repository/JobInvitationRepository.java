package com.example.repository;

import com.example.entity.JobInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface JobInvitationRepository extends JpaRepository<JobInvitation, Long> {
    Optional<JobInvitation> findByInviterIdAndRequestId(Long inviterId, String requestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from JobInvitation i where i.id=:id")
    Optional<JobInvitation> findLocked(@Param("id") Long id);
    @Modifying
    @Query("update JobInvitation i set i.status=:expired where i.vacancyId=:vacancyId and i.status=:pending and i.expiresAt<=:now")
    int expirePending(@Param("vacancyId") Long vacancyId, @Param("now") Instant now,
            @Param("pending") JobInvitation.Status pending, @Param("expired") JobInvitation.Status expired);
    boolean existsByVacancyIdAndCandidateIdAndStatus(Long vacancyId, Long candidateId, JobInvitation.Status status);
    long countByInviterIdAndVacancyIdAndCreatedAtAfter(Long inviterId, Long vacancyId, Instant after);
    long countByInviterIdAndCreatedAtAfter(Long inviterId, Instant after);
    Page<JobInvitation> findByCandidateId(Long candidateId, Pageable pageable);
    Page<JobInvitation> findByInviterId(Long inviterId, Pageable pageable);
}
