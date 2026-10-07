package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.favorite.*;
import com.example.dto.vacancy.VacancyDTO;
import com.example.enums.AppLanguage;
import com.example.service.VacancyFavoriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vacancy-favorites")
@PreAuthorize("hasAnyRole('BUYER','SELLER')")
public class VacancyFavoriteController {
    private final VacancyFavoriteService service;

    @GetMapping
    public ApiResponse<PageImpl<VacancyDTO>> getFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.getFavorites(page, perPage, language));
    }

    @GetMapping("/count")
    public ApiResponse<VacancyFavoriteCountResponse> getCount(
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.getCount(language));
    }

    @PostMapping("/{vacancyId}")
    public ApiResponse<VacancyFavoriteResponse> add(@PathVariable Long vacancyId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.add(vacancyId, language));
    }

    @DeleteMapping("/{vacancyId}")
    public ApiResponse<VacancyFavoriteResponse> remove(@PathVariable Long vacancyId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.remove(vacancyId, language));
    }
}