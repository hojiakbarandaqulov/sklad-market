package org.example.repository;

import org.example.dto.CompanyLocationResponse;
import org.example.entity.Company;
import org.example.enums.VerificationStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CompanyLocationRepository extends Repository<Company, Long> {

    @Query("""
            select c.id, 'COMPANY', c.id, c.name, c.address, c.lat, c.lng
            from Company c
            where c.deleted = false and c.deletedAt is null and c.isBlocked = false
                and c.verificationStatus in :statuses
                and c.address is not null and trim(c.address) <> ''
            """)
    List<CompanyLocationResponse> findCompanyLocations(@Param("statuses") List<VerificationStatus> statuses);

    @Query("""
            select b.id, 'BRANCH', c.id, b.branchName, b.address, b.lat, b.lng
            from CompanyBranch b join b.company c
            where b.deleted = false
                and c.deleted = false and c.deletedAt is null and c.isBlocked = false
                and c.verificationStatus in :statuses
                and b.address is not null and trim(b.address) <> ''
            """)
    List<CompanyLocationResponse> findBranchLocations(@Param("statuses") List<VerificationStatus> statuses);
}
