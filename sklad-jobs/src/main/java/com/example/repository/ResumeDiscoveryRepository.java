package com.example.repository;

import com.example.entity.ResumeDiscovery;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ResumeDiscoveryRepository extends JpaRepository<ResumeDiscovery, Long>, JpaSpecificationExecutor<ResumeDiscovery> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ResumeDiscovery d join fetch d.resume where d.resumeId = :id")
    Optional<ResumeDiscovery> findLocked(@Param("id") Long id);
}
