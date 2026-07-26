package org.example.repository;

import org.example.entity.CompanyBranch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyBranchRepository extends JpaRepository<CompanyBranch, Long> {

    Long countByCompany_IdAndDeletedFalse(Long companyId);

    List<CompanyBranch> findAllByCompany_IdAndDeletedFalseOrderByCreatedDateDesc(Long companyId);

    Optional<CompanyBranch> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
}
