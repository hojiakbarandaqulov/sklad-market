package com.example.service;

import com.example.dto.favorite.*;
import com.example.dto.vacancy.VacancyDTO;
import com.example.enums.AppLanguage;
import org.springframework.data.domain.PageImpl;

public interface VacancyFavoriteService {
    PageImpl<VacancyDTO> getFavorites(int page, int perPage, AppLanguage language);
    VacancyFavoriteCountResponse getCount(AppLanguage language);
    VacancyFavoriteResponse add(Long vacancyId, AppLanguage language);
    VacancyFavoriteResponse remove(Long vacancyId, AppLanguage language);
}