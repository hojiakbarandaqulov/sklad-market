package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.ApiResponse;
import org.example.dto.CompanyBranchCreateDTO;
import org.example.dto.CompanyBranchResponse;
import org.example.entity.Company;
import org.example.entity.CompanyBranch;
import org.example.enums.AppLanguage;
import org.example.exp.AppBadException;
import org.example.repository.CompanyBranchRepository;
import org.example.service.CompanyBranchService;
import org.example.service.CompanyService;
import org.example.service.ResourceBundleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class CompanyBranchServiceImpl implements CompanyBranchService {

    private final CompanyBranchRepository companyBranchRepository;
    private final CompanyService companyService;
    private final ResourceBundleService messageService;

    @Override
    public ApiResponse<CompanyBranchCreateDTO> create(CompanyBranchCreateDTO companyBranch, Long companyId, AppLanguage language) {
        Company ownedCompany = companyService.findOwnedCompany(companyId, language);
        if (Boolean.TRUE.equals(ownedCompany.getIsBlocked())) {
            throw new AppBadException(messageService.getMessage("account.locked", language));
        }

        CompanyBranch companyBranchEntity = new CompanyBranch();
        companyBranchEntity.setCompany(ownedCompany);
        companyBranchEntity.setBranchName(companyBranch.getName());
        companyBranchEntity.setAddress(companyBranch.getAddress());
        companyBranchEntity.setPhone(companyBranch.getPhone());
        companyBranchEntity.setLng(companyBranch.getLng());
        companyBranchEntity.setLat(companyBranch.getLat());

        CompanyBranch save = companyBranchRepository.save(companyBranchEntity);

        CompanyBranchCreateDTO createDTO = new CompanyBranchCreateDTO();
        createDTO.setName(save.getBranchName());
        createDTO.setAddress(save.getAddress());
        createDTO.setPhone(save.getPhone());
        createDTO.setLng(save.getLng());
        createDTO.setLat(save.getLat());
        return ApiResponse.successResponse(createDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<CompanyBranchResponse>> getBranches(Long companyId, AppLanguage language) {
        companyService.findOwnedCompany(companyId, language);

        List<CompanyBranchResponse> branches = companyBranchRepository
                .findAllByCompany_IdAndDeletedFalseOrderByCreatedDateDesc(companyId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ApiResponse.successResponse(branches);
    }

    @Override
    @Transactional
    public ApiResponse<CompanyBranchResponse> update(Long companyId,
                                                     Long branchId,
                                                     CompanyBranchCreateDTO request,
                                                     AppLanguage language) {
        Company ownedCompany = companyService.findOwnedCompany(companyId, language);
        checkCompanyNotBlocked(ownedCompany, language);

        CompanyBranch branch = findOwnedBranch(branchId, companyId, language);
        branch.setBranchName(request.getName());
        branch.setAddress(request.getAddress());
        branch.setPhone(request.getPhone());
        branch.setLng(request.getLng());
        branch.setLat(request.getLat());

        return ApiResponse.successResponse(toResponse(companyBranchRepository.save(branch)));
    }

    @Override
    @Transactional
    public ApiResponse<Boolean> delete(Long companyId, Long branchId, AppLanguage language) {
        Company ownedCompany = companyService.findOwnedCompany(companyId, language);
        checkCompanyNotBlocked(ownedCompany, language);

        CompanyBranch branch = findOwnedBranch(branchId, companyId, language);
        branch.setDeleted(Boolean.TRUE);
        companyBranchRepository.save(branch);
        return ApiResponse.successResponse(Boolean.TRUE);
    }

    private CompanyBranch findOwnedBranch(Long branchId, Long companyId, AppLanguage language) {
        return companyBranchRepository.findByIdAndCompany_IdAndDeletedFalse(branchId, companyId)
                .orElseThrow(() -> new AppBadException(messageService.getMessage("company.not.found", language)));
    }

    private void checkCompanyNotBlocked(Company company, AppLanguage language) {
        if (Boolean.TRUE.equals(company.getIsBlocked())) {
            throw new AppBadException(messageService.getMessage("account.locked", language));
        }
    }

    private CompanyBranchResponse toResponse(CompanyBranch branch) {
        CompanyBranchResponse response = new CompanyBranchResponse();
        response.setId(branch.getId());
        response.setCompanyId(branch.getCompany().getId());
        response.setName(branch.getBranchName());
        response.setAddress(branch.getAddress());
        response.setPhone(branch.getPhone());
        response.setLng(branch.getLng());
        response.setLat(branch.getLat());
        return response;
    }
}
