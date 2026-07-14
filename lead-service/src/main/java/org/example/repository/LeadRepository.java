package org.example.repository;

import org.example.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {
    long countBySellerIdAndDeletedFalse(Long sellerId);

    long countByCompanyIdInAndDeletedFalse(List<Long> companyIds);

    @Query("""
            select function('date_trunc', 'month', l.createdDate), count(l.id)
            from Lead l
            where l.deleted = false
              and l.companyId in :companyIds
              and l.createdDate >= :from
            group by function('date_trunc', 'month', l.createdDate)
            order by function('date_trunc', 'month', l.createdDate)
            """)
    List<Object[]> countMonthlyByCompanyIds(@Param("companyIds") List<Long> companyIds,
                                            @Param("from") LocalDateTime from);
}
