package org.example.controller;

import jakarta.validation.Valid;
import org.example.ai.guardrail.AiRoleQuotaService;
import org.example.dto.AiRoleQuotaDto;
import org.example.dto.ApiResponse;
import org.example.dto.UpdateAiRoleQuotaRequest;
import org.example.security.AiSecurityUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/admin/role-quotas")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class AiRoleQuotaAdminController {

    private final AiRoleQuotaService quotaService;

    public AiRoleQuotaAdminController(AiRoleQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping
    public ApiResponse<List<AiRoleQuotaDto>> list() {
        return ApiResponse.successResponse(quotaService.listPolicies());
    }

    @PutMapping("/{roleName}")
    public ApiResponse<AiRoleQuotaDto> update(
            @PathVariable String roleName,
            @Valid @RequestBody UpdateAiRoleQuotaRequest request) {
        return ApiResponse.successResponse(quotaService.updatePolicy(
                roleName,
                request.hourlyRequestLimit(),
                request.dailyRequestLimit(),
                AiSecurityUtil.requireSub()));
    }
}
