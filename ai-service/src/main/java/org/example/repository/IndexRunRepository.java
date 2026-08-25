package org.example.repository;

import org.example.entity.IndexRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IndexRunRepository extends JpaRepository<IndexRun, Long> {

    Optional<IndexRun> findFirstByOrderByLastRunAtDescIdDesc();
}
