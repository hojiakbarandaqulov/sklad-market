package org.example.controller;

import jakarta.annotation.security.PermitAll;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.CompanyLocationResponse;
import org.example.service.CompanyLocationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/companies/locations")
public class CompanyLocationController {

    private final CompanyLocationService companyLocationService;

    @PermitAll
    @GetMapping
    public ApiResponse<List<CompanyLocationResponse>> getLocations() {
        return ApiResponse.successResponse(companyLocationService.getLocations());
    }
}
