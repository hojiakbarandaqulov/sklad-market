package org.example.repository;

import org.example.entity.CompanyMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyMemberRepository extends JpaRepository<CompanyMember, Long> {
    Optional<CompanyMember> findByCompanyIdAndProfileId(Long companyId, Long profileId);

    List<CompanyMember> findAllByCompanyIdAndDeletedFalseOrderByIdAsc(Long companyId);
}
