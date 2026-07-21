package org.example.service;

import org.example.dto.*;
import org.example.dto.map.CompanyLocationUpdate;
import org.example.dto.map.CompanyMapResponse;
import org.example.dto.map.CompanySlugMapResponse;
import org.example.entity.Company;
import org.example.enums.AppLanguage;
import org.example.enums.VerificationStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface CompanyService {

    ApiResponse<CompanyResponseDTO> create(CompanyRequestDTO company, AppLanguage language);

    ApiResponse<CompanyInfoDTO> getMyCompanies(AppLanguage language);

    ApiResponse<PageImpl<CompanyShortDTO>> getPublicCompanies(int page, int perPage, AppLanguage language);

    ApiResponse<PageImpl<CompanyShortDTO>> search(String q, Boolean verified,  Long regionId, int page, int perPage, AppLanguage language);

    ApiResponse<CompanySlugMapResponse> getBySlug(String slug, AppLanguage language);

    ApiResponse<PageImpl<CompanyProductResponse>> getCompanyProducts(String slug, Long categoryId, int page, int perPage, AppLanguage language);

    ApiResponse<CompanyResponseDTO> update(Long id, CompanyRequestDTO dto, AppLanguage language);

    ApiResponse<CompanyDocumentResponse> addDocument(Long id, CompanyDocumentCreateRequest request, AppLanguage language);

    void submitVerification(Long id, AppLanguage language);

    void delete(Long id, AppLanguage language);

    UploadDTO uploadLogo(Long id, MultipartFile file, AppLanguage language);

    UploadDTO uploadCoverUrl(Long companyId, MultipartFile file, AppLanguage language);

    PageImpl<CompanyMapResponse> getMapCompany(Long regionId, String q, Boolean verified, int page, int perPage, AppLanguage language);

    Optional<Company> findByIdAndDeletedAtIsNull(Long companyId);

    Company findAllByOwnerUserIdAndDeletedAtIsNull(Long sellerId);

    Long countByVerificationStatusAndDeletedAtIsNull(VerificationStatus verificationStatus);

    List<CompanyMapResponse> findAllByIdInAndDeletedAtIsNullAndIsBlockedFalseAndLatNotNullAndLngNotNull(List<Long> companyIds);

    ApiResponse<CompanyLocationUpdate> companyLocationUpdate(Long companyId, CompanyLocationUpdate companyLocationUpdate, AppLanguage language);
}
