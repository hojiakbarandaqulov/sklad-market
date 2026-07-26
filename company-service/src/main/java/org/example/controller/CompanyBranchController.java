package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.CompanyBranchCreateDTO;
import org.example.dto.CompanyBranchResponse;
import org.example.enums.AppLanguage;
import org.example.service.CompanyBranchService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/companies")
public class CompanyBranchController {
    private final CompanyBranchService companyBranchService;

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("/create/{companyId}/branch")
    public ApiResponse<CompanyBranchCreateDTO> companyBranchCreate(@RequestBody @Valid CompanyBranchCreateDTO companyBranch,
                                                                   @PathVariable Long companyId,
                                                                   @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return companyBranchService.create(companyBranch, companyId, language);
    }

    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/{companyId}/branches")
    public ApiResponse<List<CompanyBranchResponse>> getBranches(
            @PathVariable Long companyId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return companyBranchService.getBranches(companyId, language);
    }

    @PreAuthorize("hasRole('SELLER')")
    @PutMapping("/{companyId}/branches/{branchId}")
    public ApiResponse<CompanyBranchResponse> updateBranch(
            @PathVariable Long companyId,
            @PathVariable Long branchId,
            @RequestBody @Valid CompanyBranchCreateDTO request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return companyBranchService.update(companyId, branchId, request, language);
    }

    @PreAuthorize("hasRole('SELLER')")
    @DeleteMapping("/{companyId}/branches/{branchId}")
    public ApiResponse<Boolean> deleteBranch(
            @PathVariable Long companyId,
            @PathVariable Long branchId,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return companyBranchService.delete(companyId, branchId, language);
    }
}
