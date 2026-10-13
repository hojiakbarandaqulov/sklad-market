package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.category.JobCategoryDTO;
import com.example.enums.AppLanguage;
import com.example.service.JobCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/job-categories")
public class JobCategoryController {
    private final JobCategoryService service;

    @GetMapping
    public ApiResponse<PageImpl<JobCategoryDTO>> list(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.list(page, perPage, true, language));
    }

    @GetMapping("/{id}")
    public ApiResponse<JobCategoryDTO> get(@PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.get(id, true, language));
    }
}
