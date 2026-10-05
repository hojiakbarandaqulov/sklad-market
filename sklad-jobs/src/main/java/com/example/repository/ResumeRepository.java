package com.example.repository;

import com.example.entity.Resume;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    Optional<Resume> findByIdAndCandidateIdAndDeletedFalse(Long id, Long candidateId);
    Page<Resume> findAllByCandidateIdAndDeletedFalse(Long candidateId, Pageable pageable);

    Optional<Resume> findByIdAndDeletedFalse(Long id);

}