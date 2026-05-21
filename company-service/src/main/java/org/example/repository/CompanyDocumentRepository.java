package org.example.repository;

import org.example.entity.CompanyDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyDocumentRepository extends JpaRepository<CompanyDocument, Long> {
}
