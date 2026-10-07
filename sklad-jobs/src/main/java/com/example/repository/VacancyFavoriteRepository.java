package com.example.repository;

import com.example.entity.Vacancy;
import com.example.entity.VacancyFavorite;
import com.example.enums.VacancyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface VacancyFavoriteRepository extends JpaRepository<VacancyFavorite, Long> {
    Optional<VacancyFavorite> findByUserIdAndVacancyId(Long userId, Long vacancyId);

    @Query(value = """
        select v from Vacancy v, VacancyFavorite f
        where f.vacancyId = v.id and f.userId = :userId
          and f.isActive = true and f.deleted = false
          and v.deleted = false and v.vacancyStatus = :status
        order by f.createdDate desc, f.id desc
        """, countQuery = """
        select count(v) from Vacancy v, VacancyFavorite f
        where f.vacancyId = v.id and f.userId = :userId
          and f.isActive = true and f.deleted = false
          and v.deleted = false and v.vacancyStatus = :status
        """)
    Page<Vacancy> findVisibleFavorites(@Param("userId") Long userId,
            @Param("status") VacancyStatus status, Pageable pageable);

    @Query("""
        select count(v) from Vacancy v, VacancyFavorite f
        where f.vacancyId = v.id and f.userId = :userId
          and f.isActive = true and f.deleted = false
          and v.deleted = false and v.vacancyStatus = :status
        """)
    long countVisibleFavorites(@Param("userId") Long userId, @Param("status") VacancyStatus status);

    @Modifying
    @Query("""
        update VacancyFavorite f set f.isActive = false, f.modifiedDate = CURRENT_TIMESTAMP
        where f.userId = :userId and f.vacancyId = :vacancyId
          and f.isActive = true and f.deleted = false
        """)
    int deactivate(@Param("userId") Long userId, @Param("vacancyId") Long vacancyId);
}