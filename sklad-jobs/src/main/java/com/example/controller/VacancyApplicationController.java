package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.application.*;
import com.example.enums.AppLanguage;
import com.example.service.JobApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/vacancies/{id}/applications")
@PreAuthorize("hasRole('BUYER')")
public class VacancyApplicationController {

    private final JobApplicationService jobApplicationService;

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JobApplicationDTO> create(@PathVariable Long id,
            @Valid @RequestBody ApplicationCreateRequest request,
            @RequestHeader(value="Accept-Language",defaultValue="UZ") AppLanguage language) {
        return ApiResponse.successResponse(jobApplicationService.create(id,request,language));
    }

}