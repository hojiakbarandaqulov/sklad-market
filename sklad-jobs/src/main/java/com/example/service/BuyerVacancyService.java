package com.example.service;

import com.example.dto.ApiResponse;
import com.example.dto.vacancy.VacancyDTO;
import com.example.dto.vacancy.VacancyRequest;
import com.example.enums.AppLanguage;

public interface BuyerVacancyService {
    VacancyDTO update(Long id, VacancyRequest request, AppLanguage language);
    ApiResponse<String> close(Long id, AppLanguage language);
    ApiResponse<String> archive(Long id, AppLanguage language);
    ApiResponse<String> submit(Long id, AppLanguage language);
}