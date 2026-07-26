package org.example.service;

import org.example.dto.ApiResponse;
import org.example.dto.CompanyBranchCreateDTO;
import org.example.dto.CompanyBranchResponse;
import org.example.enums.AppLanguage;

import java.util.List;

public interface CompanyBranchService {

    ApiResponse<CompanyBranchCreateDTO> create(CompanyBranchCreateDTO companyBranch, Long companyId, AppLanguage language);

    ApiResponse<List<CompanyBranchResponse>> getBranches(Long companyId, AppLanguage language);

    ApiResponse<CompanyBranchResponse> update(Long companyId, Long branchId, CompanyBranchCreateDTO request, AppLanguage language);

    ApiResponse<Boolean> delete(Long companyId, Long branchId, AppLanguage language);
}
