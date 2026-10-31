package com.example.service;

import com.example.dto.statistics.ControlStatisticsDTO;
import com.example.enums.AppLanguage;

public interface ControlStatisticsService {
    ControlStatisticsDTO getStatistics(Long companyId, AppLanguage language);
}
