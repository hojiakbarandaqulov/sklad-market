package com.example.controller;
import com.example.dto.ApiResponse;
import com.example.dto.vacancy.*;
import com.example.enums.AppLanguage;
import com.example.service.VacancyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/vacancies")
public class PublicVacancyController {
    private final VacancyService vacancyService;

    @GetMapping
    public ApiResponse<PageImpl<PublicVacancyDTO>> getVacancies(@Valid @ModelAttribute VacancyFilter filter,
            @RequestHeader(value="Accept-Language",defaultValue="UZ") AppLanguage language) {
        return ApiResponse.successResponse(vacancyService.getVacancies(filter,language));
    }

    @GetMapping("/{id}")
    public ApiResponse<PublicVacancyDTO> getVacancy(@PathVariable Long id,
            @RequestHeader(value="Accept-Language",defaultValue="UZ") AppLanguage language) {
        return ApiResponse.successResponse(vacancyService.getVacancy(id,language));
    }

}