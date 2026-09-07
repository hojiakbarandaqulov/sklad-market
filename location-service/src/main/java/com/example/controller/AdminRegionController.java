package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.RegionDTO;
import com.example.dto.RegionResponse;
import com.example.enums.AppLanguage;
import com.example.service.RegionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/regions")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AdminRegionController {

    private final RegionService regionService;

    @PostMapping
    public ApiResponse<RegionResponse> create(
            @RequestBody @Valid RegionDTO request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(regionService.create(request, language));
    }

    @PutMapping("/{id}")
    public ApiResponse<RegionResponse> update(
            @PathVariable Long id,
            @RequestBody @Valid RegionDTO request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(regionService.update(id, request, language));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(
            @PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        regionService.delete(id, language);
        return ApiResponse.successResponse(Boolean.TRUE);
    }
}
