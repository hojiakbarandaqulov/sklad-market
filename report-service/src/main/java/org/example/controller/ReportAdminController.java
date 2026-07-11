package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.report.*;
import org.example.enums.AppLanguage;
import org.example.enums.ReportStatus;
import org.example.enums.TargetType;
import org.example.exp.AppBadException;
import org.example.service.ReportService;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/admin/reports")
public class ReportAdminController {
    private final ReportService reportService;

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @GetMapping
    public ApiResponse<PageImpl<ReportResponse>> getReport(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) TargetType targetType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return ApiResponse.successResponse(reportService.getReport(parseStatus(status), targetType, page, size, language));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @GetMapping("/{id}")
    public ApiResponse<ReportInfoResponse> getByReport(
            @PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return ApiResponse.successResponse(reportService.getByReport(id, language));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @PutMapping("/{id}/resolve")
    public ApiResponse<ReportResolveResponse> reportResolve(
            @PathVariable Long id,
            @RequestBody ReportResolveRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return ApiResponse.successResponse(reportService.reportResolve(id, request, language));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @PutMapping("/{id}/reject")
    public ApiResponse<ReportResolveResponse> reportReject(
            @PathVariable Long id,
            @RequestBody ReportResolveRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return ApiResponse.successResponse(reportService.reportReject(id, request, language));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @PutMapping("/{id}/warn-user")
    public ApiResponse<ReportResolveResponse> warnUser(
            @PathVariable Long id,
            @RequestBody @Valid ReportWarnRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return ApiResponse.successResponse(reportService.warnUser(id, request, language));
    }

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @PutMapping("/{id}/block-target")
    public ApiResponse<ReportResolveResponse> blockTarget(
            @PathVariable Long id,
            @RequestBody @Valid ReportBlockRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language
    ) {
        return ApiResponse.successResponse(reportService.blockTarget(id, request, language));
    }

    private ReportStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }

        String normalized = rawStatus.trim().toUpperCase(Locale.ROOT);
        if ("PENDING".equals(normalized)) {
            return ReportStatus.NEW;
        }

        try {
            return ReportStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new AppBadException("Invalid report status: " + rawStatus);
        }
    }
}
