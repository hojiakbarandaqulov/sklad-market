package com.example.service;

import com.example.dto.ApiResponse;
import com.example.dto.vacancy.VacancyCreate;
import com.example.dto.vacancy.VacancyDTO;
import com.example.dto.vacancy.VacancyRequest;
import com.example.enums.AppLanguage;
import com.example.enums.VacancyModeration;
import org.springframework.data.domain.PageImpl;

public interface VacancyService {
    VacancyDTO createVacancy(VacancyCreate vacancyCreate, AppLanguage language);

    VacancyDTO updateVacancy(VacancyRequest vacancyUpdate, Long vacancyId, AppLanguage language);

    PageImpl<VacancyDTO> getCompanyVacancy(Long vacancyId, int page, int perPage, AppLanguage language);

    ApiResponse<String> submitVacancyModeration(Long vacancyId, AppLanguage language);

    ApiResponse<String> closeVacancy(Long vacancyId, AppLanguage language);

    ApiResponse<String> archiveVacancy(Long vacancyId, AppLanguage language);

    ApiResponse<String> vacancyModeration(Long vacancyId, VacancyModeration vacancyModeration, AppLanguage language);
}
