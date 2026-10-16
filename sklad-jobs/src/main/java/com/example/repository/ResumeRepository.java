package com.example.repository;

import com.example.entity.Resume;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Resume r where r.id=:id and r.candidateId=:candidateId and r.deleted=false")
    Optional<Resume> findOwnedForDiscovery(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("candidateId") Long candidateId);
    Optional<Resume> findByIdAndCandidateIdAndDeletedFalse(Long id, Long candidateId);
    Page<Resume> findAllByCandidateIdAndDeletedFalse(Long candidateId, Pageable pageable);

    Optional<Resume> findByIdAndDeletedFalse(Long id);

}
