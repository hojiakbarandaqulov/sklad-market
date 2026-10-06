package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.vacancy.*;
import com.example.enums.AppLanguage;
import com.example.service.BuyerVacancyService;
import com.example.service.VacancyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/vacancy/buyer")
@PreAuthorize("hasRole('BUYER')")
public class BuyerVacancyController {
    private final BuyerVacancyService buyerVacancyService;
    private final VacancyService vacancyService;

    @PostMapping
    public ApiResponse<VacancyCreateBuyerResponseDTO> create(@Valid @RequestBody VacancyCreateBuyer request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(vacancyService.createVacancyBuyer(request, language));
    }

    @GetMapping
    public ApiResponse<PageImpl<VacancyDTO>> getMyVacancies(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(vacancyService.getMyVacancy(page, perPage, language));
    }

    @PutMapping("/{id}")
    public ApiResponse<VacancyDTO> update(@PathVariable Long id, @Valid @RequestBody VacancyRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(buyerVacancyService.update(id, request, language));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<String> close(@PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return buyerVacancyService.close(id, language);
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<String> archive(@PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return buyerVacancyService.archive(id, language);
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<String> submit(@PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return buyerVacancyService.submit(id, language);
    }
}