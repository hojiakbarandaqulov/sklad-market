package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.CompanyResponseDTO;
import org.example.dto.admin.ModerationDecisionRequest;
import org.example.dto.admin.ReasonRequest;
import org.example.enums.AppLanguage;
import org.example.enums.VerificationStatus;
import org.example.service.AdminCompanyService;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/companies")
public class AdminCompanyController {

    private final AdminCompanyService adminCompanyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<PageImpl<CompanyResponseDTO>> getCompanies(
            @RequestParam(required = false) VerificationStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(adminCompanyService.getCompanies(status, q, page, perPage, language));
    }

    @GetMapping("/moderation-queue")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<List<CompanyResponseDTO>> getModerationQueue(@RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return ApiResponse.successResponse(adminCompanyService.getModerationQueue());
    }

    @PutMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, String>> verify(@PathVariable Long id,
                                                   @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        adminCompanyService.verify(id, language);
        return ApiResponse.successResponse(Map.of("message", "Company verified", "status", VerificationStatus.VERIFIED.name()));
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, String>> reject(@PathVariable Long id,
                                                   @RequestBody(required = false) ModerationDecisionRequest request,
                                                   @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        adminCompanyService.reject(id, request, language);
        return ApiResponse.successResponse(Map.of("message", "Company rejected", "status", VerificationStatus.REJECTED.name()));
    }

    @PutMapping("/{id}/block")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<Map<String, String>> block(@PathVariable Long id,
                                                  @RequestBody(required = false) ReasonRequest request,
                                                  @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        adminCompanyService.block(id, request, language);
        return ApiResponse.successResponse(Map.of("message", "Company blocked", "blocked", Boolean.TRUE.toString()));
    }
    
}
