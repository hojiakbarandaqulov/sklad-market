package com.example.service;

import com.example.dto.AddressSuggestion;
import com.example.dto.RegionDTO;
import com.example.dto.RegionResponse;
import com.example.enums.AppLanguage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface RegionService {
    RegionResponse create(RegionDTO request, AppLanguage language);

    RegionResponse update(Long id, RegionDTO request, AppLanguage language);

    PageImpl<RegionResponse> getAll(Pageable pageable, AppLanguage language);

    RegionResponse getById(Long id, AppLanguage language);

    void delete(Long id, AppLanguage language);

    boolean existsByRegion(Long regionId);

    Page<RegionResponse> regionSearch(String q, Pageable pageable, AppLanguage language);
}
