package org.example.controller;

import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.*;
import org.example.dto.map.CompanyMapResponse;
import org.example.dto.map.CompanySlugMapResponse;
import org.example.enums.AppLanguage;
import org.example.service.CompanyService;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService companyService;

    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("/create")
    public ApiResponse<CompanyResponseDTO> createCompany(@RequestBody @Valid CompanyRequestDTO company,
                                                         @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return companyService.create(company, language);
    }

    @GetMapping
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<CompanyInfoDTO> getMyCompanies(@RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return companyService.getMyCompanies(language);
    }

    @PermitAll
    @GetMapping("/public")
    public ApiResponse<PageImpl<CompanyShortDTO>> getPublicCompanies(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return companyService.getPublicCompanies(page, perPage, language);
    }

    @PermitAll
    @GetMapping("/search")
    public ApiResponse<PageImpl<CompanyShortDTO>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean verified,
//            @RequestParam(required = false) Long category,
            @RequestParam(required = false) Long regionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return companyService.search(q, verified,  regionId, page, perPage, language);
    }

    @PermitAll
    @GetMapping("/{slug}")
    public ApiResponse<CompanySlugMapResponse> getBySlug(
            @PathVariable String slug,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return companyService.getBySlug(slug, language);
    }

    @GetMapping("/map")
    public ApiResponse<PageImpl<CompanyMapResponse>> getMapCompanies(
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Boolean verified,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        PageImpl<CompanyMapResponse> responses = companyService.getMapCompany(regionId, q, verified, page, perPage,language);
        return ApiResponse.successResponse(responses);
    }

    @PermitAll
    @GetMapping("/{slug}/products")
    public ApiResponse<PageImpl<CompanyProductResponse>> getCompanyProducts(
            @PathVariable String slug,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(value = "per_page", defaultValue = "20") int perPage,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return companyService.getCompanyProducts(slug, page, perPage, language);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<CompanyResponseDTO> update(
            @PathVariable Long id,
            @RequestBody @Valid CompanyRequestDTO dto,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return companyService.update(id, dto, language);
    }

    @PostMapping("/{id}/documents")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<CompanyDocumentResponse> addDocument(
            @PathVariable Long id,
            @RequestBody @Valid CompanyDocumentCreateRequest request,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        return companyService.addDocument(id, request, language);
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<UploadDTO> uploadLogo(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        UploadDTO logoUrl = companyService.uploadLogo(id, file, language);
        return ApiResponse.successResponse(logoUrl);
    }

    @PostMapping(value = "/{companyId}/coverUrl", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<UploadDTO> uploadCoverUrl(
            @PathVariable Long companyId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        UploadDTO coverUrl = companyService.uploadCoverUrl(companyId, file, language);
        return ApiResponse.successResponse(coverUrl);
    }

    @PostMapping("/{id}/submit-verification")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<?> submitVerification(
            @PathVariable Long id,
            @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        companyService.submitVerification(id, language);
        return new ApiResponse<>("Moderatsiyaga yuborildi",true);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ApiResponse<Boolean> delete(@PathVariable Long id,
                                       @RequestHeader(value = "Accept-Language", defaultValue = "UZ") AppLanguage language) {
        companyService.delete(id, language);
        return ApiResponse.successResponse(true);
    }
}
