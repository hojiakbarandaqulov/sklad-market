package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.category.JobCategoryDTO;
import com.example.dto.category.JobCategoryRequest;
import com.example.enums.AppLanguage;
import com.example.service.JobCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/job-categories")
public class AdminJobCategoryController {
    private final JobCategoryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JobCategoryDTO> create(@RequestBody @Valid JobCategoryRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.create(request, language));
    }

    @PutMapping("/{id}")
    public ApiResponse<JobCategoryDTO> update(@PathVariable Long id, @RequestBody @Valid JobCategoryRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.update(id, request, language));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        service.delete(id, language);
    }

    @GetMapping
    public ApiResponse<PageImpl<JobCategoryDTO>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.list(page, perPage, false, language));
    }

    @GetMapping("/{id}")
    public ApiResponse<JobCategoryDTO> get(@PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.get(id, false, language));
    }
}
