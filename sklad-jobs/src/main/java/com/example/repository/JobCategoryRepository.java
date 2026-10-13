package com.example.repository;

import com.example.entity.JobCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobCategoryRepository extends JpaRepository<JobCategory, Long> {
    Page<JobCategory> findAllByDeletedFalse(Pageable pageable);

    Optional<JobCategory> findByIdAndDeletedFalse(Long id);
}
