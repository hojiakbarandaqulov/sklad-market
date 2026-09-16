package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.vacancy.VacancyCreate;
import com.example.dto.vacancy.VacancyDTO;
import com.example.dto.vacancy.VacancyRequest;
import com.example.enums.AppLanguage;
import com.example.enums.VacancyModeration;
import com.example.service.VacancyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/v1/vacancy")
public class VacancyController {
    private final VacancyService vacancyService;

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("create")
    public ApiResponse<VacancyDTO> vacancyCreate(@RequestBody @Valid VacancyCreate vacancyDTO,
                                                 @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        VacancyDTO vacancyDTOResult = vacancyService.createVacancy(vacancyDTO, language);
        return ApiResponse.successResponse(vacancyDTOResult);
    }

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("update")
    public ApiResponse<VacancyDTO> vacancyUpdate(@RequestBody @Valid VacancyRequest vacancyUpdate,
                                                 @RequestParam Long vacancyId,
                                                 @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        VacancyDTO vacancyDTOResult = vacancyService.updateVacancy(vacancyUpdate, vacancyId, language);
        return ApiResponse.successResponse(vacancyDTOResult);
    }

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("company")
    public ApiResponse<PageImpl<VacancyDTO>> getCompanyVacancy(@RequestParam Long companyId,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "20") int perPage,
                                                              @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        PageImpl<VacancyDTO> vacancyDTOResult = vacancyService.getCompanyVacancy(companyId,page,perPage, language);
        return ApiResponse.successResponse(vacancyDTOResult);
    }

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("{vacancyId}/submit")
    public ApiResponse<String> submitModerationVacancy(@PathVariable Long vacancyId,
                                                       @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return vacancyService.submitVacancyModeration(vacancyId, language);
    }

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("{vacancyId}/close")
    public ApiResponse<String> closeVacancy(@PathVariable Long vacancyId,
                                            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return vacancyService.closeVacancy(vacancyId, language);
    }

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("{vacancyId}/archive")
    public ApiResponse<String> archiveVacancy(@PathVariable Long vacancyId,
                                              @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return vacancyService.archiveVacancy(vacancyId, language);
    }


    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("admin/{vacancyId}/moderation")
    public ApiResponse<String> vacancyModeration(@PathVariable Long vacancyId,
                                              @RequestParam VacancyModeration vacancyModeration,
                                              @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return vacancyService.vacancyModeration(vacancyId, vacancyModeration, language);
    }

}
