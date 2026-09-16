package com.example.repository;

import com.example.entity.Vacancy;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

    Vacancy findByCompanyIdAndDeletedFalse(Long ownedCompanyIds);

    Optional<Vacancy>  findByIdAndDeletedFalse(Long vacancyId);

    PageImpl<Vacancy> findAllByCompanyIdAndDeletedFalse(Long companyId, Pageable pageable);
}
