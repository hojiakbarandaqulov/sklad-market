package org.example.repository;

import org.example.entity.ToolAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ToolAuditRepository extends JpaRepository<ToolAudit, UUID> {
}
