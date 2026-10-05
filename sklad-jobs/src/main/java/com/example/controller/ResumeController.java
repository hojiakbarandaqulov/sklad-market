package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.resume.ResumeDTO;
import com.example.dto.resume.ResumeImageResponse;
import com.example.dto.resume.ResumeRequest;
import com.example.enums.AppLanguage;
import com.example.service.ResumeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/v1/me/resumes")
public class ResumeController {
    private final ResumeService resumeService;

    @PreAuthorize("hasRole('BUYER')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ResumeDTO> createResume(@RequestBody @Valid ResumeRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(resumeService.createResume(request, language));
    }
    @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    @GetMapping
    public ApiResponse<PageImpl<ResumeDTO>> getMyResumes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(resumeService.getMyResumes(page, perPage, language));
    }

    @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    @GetMapping("/{id}")
    public ApiResponse<ResumeDTO> getResume(@PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(resumeService.getResume(id, language));
    }

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<ResumeImageResponse> uploadImages(@PathVariable Long id,
                                                         @RequestParam(value = "file") MultipartFile file,
                                                         @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(resumeService.uploadImages(id, file, language));
    }
    @PreAuthorize("hasAnyRole('BUYER','SELLER')")
    @GetMapping("byId/{id}")
    public ApiResponse<ResumeDTO> getByIdResume(@PathVariable Long id,
                                            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(resumeService.getByIdResume(id, language));
    }

    @PreAuthorize("hasRole('BUYER')")
    @PutMapping("/{id}")
    public ApiResponse<ResumeDTO> updateResume(@PathVariable Long id,
            @RequestBody @Valid ResumeRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(resumeService.updateResume(id, request, language));
    }

    @PreAuthorize("hasRole('BUYER')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteResume(@PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        resumeService.deleteResume(id, language);
    }
}