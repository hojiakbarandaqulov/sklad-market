package com.example.service;

import com.example.dto.vacancy.VacancyFilter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface AiVacancyCandidatesService {
    record Candidate(Long id, String positionName, Long categoryId, BigDecimal price,
            Long regionId, String address, String employmentType, String workSchedule,
            String experienceLevel, Instant publishedAt, String shortDescription, String requirements,
            String companyName, String companyLogo) {}

    List<Candidate> find(VacancyFilter filter, List<String> terms, List<Long> ids, int limit);
}
