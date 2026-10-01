package com.example.repository;
import com.example.entity.JobApplication;
import com.example.entity.Resume;
import com.example.enums.ApplicationStatus;
import com.example.enums.GetNewApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface JobApplicationRepository extends JpaRepository<JobApplication,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths="vacancy")
    @Query("select a from JobApplication a where a.id=:id and a.deleted=false")
    Optional<JobApplication> findForChat(@Param("id") Long id);

    @Query("select a.id from JobApplication a where a.deleted=false and a.chatPending=true " +
        "and a.chatThreadId is null and (a.chatNextAttemptAt is null or a.chatNextAttemptAt<=:now) order by a.id")
    java.util.List<Long> findPendingChats(@Param("now") java.time.Instant now,Pageable pageable);
    boolean existsByVacancyIdAndCandidateIdAndDeletedFalse(Long vacancyId,Long candidateId);
    @EntityGraph(attributePaths="vacancy")
    @Query("select a from JobApplication a where a.candidateId = :candidateId and a.deleted = false and (:status is null or a.status = :status)")
    Page<JobApplication> findMyApplications(@Param("candidateId") Long candidateId,@Param("status") ApplicationStatus status,Pageable pageable);
    @EntityGraph(attributePaths="vacancy")
    Optional<JobApplication> findByIdAndCandidateIdAndDeletedFalse(Long id,Long candidateId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from JobApplication a where a.id = :id and a.candidateId = :candidateId and a.deleted = false")
    Optional<JobApplication> findOwnedForUpdate(@Param("id") Long id,@Param("candidateId") Long candidateId);

    Optional<JobApplication> findByIdAndDeletedFalse(Long id);

    Page<JobApplication> findByVacancyIdAndDeletedFalseAndStatus(Long vacancy_id, ApplicationStatus status, Pageable pageable);
}