package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.application.*;
import com.example.enums.*;
import com.example.service.JobApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/me/applications")
@PreAuthorize("hasRole('BUYER')")
public class JobApplicationController {
    private final JobApplicationService jobApplicationService;

    @GetMapping
    public ApiResponse<PageImpl<JobApplicationDTO>> getMyApplications(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(jobApplicationService.getMyApplications(status, page, perPage, language));
    }

    @GetMapping("/{id}")
    public ApiResponse<JobApplicationDTO> getMyApplication(@PathVariable Long id,
                                                           @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(jobApplicationService.getMyApplication(id, language));
    }


    @PostMapping("/{id}/withdraw")
    public ApiResponse<JobApplicationDTO> withdraw(@PathVariable Long id,
                                                   @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(jobApplicationService.withdraw(id, language));
    }

    @PatchMapping("/{id}/resume")
    public ApiResponse<JobApplicationDTO> replaceResume(@PathVariable Long id, @RequestBody @Valid ApplicationResumeRequest request,
                                                        @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(jobApplicationService.replaceResume(id, request, language));
    }

    @PreAuthorize("hasRole('SELLER')")
    @PutMapping("/{id}/status")
    public ApiResponse<JobApplicationDTO> applicationResponse(@PathVariable Long id,
                                                              @RequestParam ApplicationResponseStatus applicationResponseStatus,
                                                              @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(jobApplicationService.applicationStatusResponse(id, applicationResponseStatus, language));
    }

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/vacancy/{vacancyId}")
    public ApiResponse<Page<JobApplicationDTO>> getApplicationVacancy(@PathVariable Long vacancyId,
                                                                @RequestParam(defaultValue = "NEW") GetNewApplicationStatus status,
                                                                @RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "20") int perPage,
                                                                @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
      Page<JobApplicationDTO> jobApplicationDTO= jobApplicationService.getApplicationVacancy(vacancyId,status,page,perPage);
      return ApiResponse.successResponse(jobApplicationDTO);
    }
}