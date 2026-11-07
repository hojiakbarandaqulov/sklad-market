package org.example.repository;

import org.example.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lead l where l.id=:id and l.deleted=false")
    java.util.Optional<Lead> lockPresent(@Param("id") Long id);
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
