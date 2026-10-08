package com.example.repository;

import com.example.entity.Vacancy;
import com.example.enums.VacancyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VacancyRepository extends JpaRepository<Vacancy, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Vacancy> {

    Optional<Vacancy> findByIdAndVacancyStatusAndDeletedFalse(Long id, com.example.enums.VacancyStatus status);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select v from Vacancy v where v.id = :id and v.deleted = false")
    Optional<Vacancy> findForApplication(@org.springframework.data.repository.query.Param("id") Long id);

    Vacancy findByCompanyIdAndDeletedFalse(Long ownedCompanyIds);

    Optional<Vacancy> findByIdAndDeletedFalse(Long vacancyId);

    org.springframework.data.domain.Page<Vacancy> findAllByVacancyStatusAndDeletedFalse(com.example.enums.VacancyStatus status, Pageable pageable);

    PageImpl<Vacancy> findAllByCompanyIdAndDeletedFalse(Long companyId, Pageable pageable);

    Page<Vacancy> findByBuyerIdAndDeletedFalse(Long profileId, Pageable pageable);

    @Modifying(clearAutomatically = true,flushAutomatically = true)
    @Query("""
            UPDATE Vacancy v SET  v.viewsCountCache = COALESCE(v.viewsCountCache,0)+1
                        WHERE v.id=:id
                                    AND v.vacancyStatus=:status
                                    AND v.deleted=false
            """)
    int incrementCount(@Param("id") Long id, @Param("status") VacancyStatus status);
}
