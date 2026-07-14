package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.config.internal.FileClient;
import org.example.config.internal.ProductClient;
import org.example.dto.*;
import org.example.dto.attach.AttachDto;
import org.example.dto.kafka.CompanyCreateEvent;
import org.example.dto.map.CompanyMapResponse;
import org.example.dto.map.CompanySlugMapResponse;
import org.example.entity.Company;
import org.example.entity.CompanyDocument;
import org.example.enums.AppLanguage;
import org.example.enums.VerificationStatus;
import org.example.exp.AppBadException;
import org.example.repository.CompanyDocumentRepository;
import org.example.repository.CompanyRepository;
import org.example.service.CompanyService;
import org.example.service.KafkaProducerService;
import org.example.service.ResourceBundleService;
import org.example.utils.SpringSecurityUtil;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {
    private final CompanyRepository companyRepository;
    private final CompanyDocumentRepository companyDocumentRepository;
    private final KafkaProducerService kafkaProducerService;
    private final ModelMapper modelMapper;
    private final FileClient fileClient;
    private final ProductClient productClient;
    private final ResourceBundleService messageService;

    private static final int MAX_COMPANIES_PER_SELLER = 5;

    @Override
    public ApiResponse<CompanyResponseDTO> create(CompanyRequestDTO requestDTO, AppLanguage language) {
        Long userId = SpringSecurityUtil.getProfileId();
        long count = companyRepository.countByOwnerUserIdAndDeletedAtIsNull(userId);
        if (count >= MAX_COMPANIES_PER_SELLER) {
            throw new AppBadException(messageService.getMessage("maximum.of.5.companies.can.be.created", language));
        }
        Optional<Company> bySlug = companyRepository.findBySlug(generateSlug(requestDTO.getName()));
        if (bySlug.isPresent()) {
            throw new AppBadException(messageService.getMessage("company.slug.exists", language));
        }
        Company companyMap = modelMapper.map(requestDTO, Company.class);

        companyMap.setOwnerUserId(userId);
        companyMap.setSlug(generateSlug(requestDTO.getName()));
        companyMap.setVerificationStatus(VerificationStatus.DRAFT);

        companyMap.setIsBlocked(false);
        Company saved = companyRepository.save(companyMap);
        CompanyCreateEvent companyCreateEvent = new CompanyCreateEvent();
        companyCreateEvent.setCompanyId(companyMap.getId());
        companyCreateEvent.setCompanyName(companyMap.getName());
        companyCreateEvent.setCompanySlug(companyMap.getSlug());
        companyCreateEvent.setOwnerUserId(companyMap.getOwnerUserId());
        companyCreateEvent.setVerificationStatus(companyMap.getVerificationStatus());
        companyCreateEvent.setCreatedDate(companyMap.getCreatedDate());
        kafkaProducerService.onCompanyCreated(companyCreateEvent);
        return ApiResponse.successResponse(toResponse(saved));
    }

    @Override
    public ApiResponse<CompanyInfoDTO> getMyCompanies(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        Company companyInfo = companyRepository.findAllByOwnerUserIdAndDeletedAtIsNull(profileId);
        CompanyInfoDTO infoResponse = toInfoResponse(companyInfo);
        return ApiResponse.successResponse(infoResponse);
    }

    @Override
    public ApiResponse<PageImpl<CompanyShortDTO>> getPublicCompanies(int page, int perPage, AppLanguage language) {
        return ApiResponse.successResponse(getPublicCompanyPage(null, null,null, page, perPage, language));
    }

    @Override
    public ApiResponse<PageImpl<CompanyShortDTO>> search(String q, Boolean verified,  Long regionId, int page, int perPage, AppLanguage language) {
        return ApiResponse.successResponse(getPublicCompanyPage(q, verified, regionId, page, perPage, language));
    }

    @Override
    public ApiResponse<CompanySlugMapResponse> getBySlug(String slug, AppLanguage language) {
        Company company = companyRepository.findBySlugAndDeletedAtIsNullAndVerificationStatusIn(slug, List.of(VerificationStatus.VERIFIED, VerificationStatus.PENDING_VERIFICATION));
        if (company == null) {
            throw new AppBadException(messageService.getMessage("company.not.found", language));
        }
        return ApiResponse.successResponse(toSlugMapResponse(company));
    }

    @Override
    public ApiResponse<PageImpl<CompanyProductResponse>> getCompanyProducts(String slug, int page, int perPage, AppLanguage language) {
        int resolvedPage = normalizePage(page, language);
        int resolvedPerPage = normalizePerPage(perPage, language);
        Company company = companyRepository.findBySlugAndDeletedAtIsNullAndVerificationStatusIn(slug, List.of(VerificationStatus.VERIFIED, VerificationStatus.PENDING_VERIFICATION));
        if (company == null) {
            throw new AppBadException(messageService.getMessage("company.not.found", language));
        }

        CompanyProductListResponse productList = productClient.getCompanyProducts(company.getId(), resolvedPage, resolvedPerPage);

        if (productList == null) {
            return ApiResponse.successResponse(new PageImpl<>(List.of(), PageRequest.of(resolvedPage - 1, resolvedPerPage), 0));
        }

        return ApiResponse.successResponse(new PageImpl<>(productList.getItems() == null ? List.of() : productList.getItems(), PageRequest.of(Math.max(productList.getPage() - 1, 0), productList.getPerPage()), productList.getTotalElements() == null ? 0 : productList.getTotalElements()));
    }

    @Transactional
    @Override
    public ApiResponse<CompanyResponseDTO> update(Long id, CompanyRequestDTO dto, AppLanguage language) {
        Company company = findOwnedCompany(id, language);
        company.setName(dto.getName());
        company.setShortDescription(dto.getShortDescription());
        company.setDescription(dto.getDescription());
        company.setStir(dto.getStir());
        company.setLng(dto.getLng());
        company.setLat(dto.getLat());
        company.setPhonePrimary(dto.getPhonePrimary());
        company.setPhoneSecondary(dto.getPhoneSecondary());
        company.setWebsite(dto.getWebsite());
        company.setAddress(dto.getAddress());
        Company saved = companyRepository.save(company);
        return ApiResponse.successResponse(toResponse(saved));
    }

    @Override
    public ApiResponse<CompanyDocumentResponse> addDocument(Long id, CompanyDocumentCreateRequest request, AppLanguage language) {
        findOwnedCompany(id, language);
        CompanyDocument document = new CompanyDocument();
        document.setCompanyId(id);
        document.setDocumentType(request.getDocumentType());
        document.setAttachId(request.getAttachId());
        document.setFileUrl(request.getFileUrl());
        CompanyDocument saved = companyDocumentRepository.save(document);

        CompanyDocumentResponse response = new CompanyDocumentResponse();
        response.setId(saved.getId());
        response.setCompanyId(saved.getCompanyId());
        response.setDocumentType(saved.getDocumentType());
        response.setAttachId(saved.getAttachId());
        response.setFileUrl(saved.getFileUrl());
        response.setStatus(saved.getStatus());
        response.setCreatedAt(saved.getCreatedDate());
        return ApiResponse.successResponse(response);
    }

    @Override
    public void submitVerification(Long id, AppLanguage language) {
        Company company = findOwnedCompany(id, language);
        if (!company.getVerificationStatus().equals(VerificationStatus.DRAFT)) {
            throw new AppBadException(messageService.getMessage("company.verification.failed", language));
        }
        company.setVerificationStatus(VerificationStatus.PENDING_VERIFICATION);
        companyRepository.save(company);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Long id, AppLanguage language) {
        Company company = findOwnedCompany(id, language);
        company.setDeletedAt(LocalDateTime.now());
        company.setDeleted(true);
        companyRepository.save(company);
    }

    @Override
    public UploadDTO uploadLogo(Long id, MultipartFile file, AppLanguage language) {
        Company company = findOwnedCompany(id, language);
       /* HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<UploadDTO> response = restTemplate.postForEntity("http://localhost:8090/api/v1/attach/upload", requestEntity, UploadDTO.class);*/
        ApiResponse<AttachDto> uploadDTO = fileClient.upload(file,language.name());
        if (uploadDTO == null) {
            throw new AppBadException(messageService.getMessage("logo.not.download", language));
        }
        company.setLogoPath(uploadDTO.getData().getUrl());
        companyRepository.save(company);
        return new UploadDTO(uploadDTO.getData().getId(), company.getLogoPath());
    }

    @Override
    public UploadDTO uploadCoverUrl(Long companyId, MultipartFile file, AppLanguage language) {
        Company company = findOwnedCompany(companyId, language);
       /* HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<UploadDTO> response = restTemplate.postForEntity("http://localhost:8090/api/v1/attach/upload", requestEntity, UploadDTO.class);*/
        ApiResponse<AttachDto> uploadDTO = fileClient.upload(file,language.name());
        if (uploadDTO == null) {
            throw new AppBadException(messageService.getMessage("logo.not.download", language));
        }
        company.setCoverUrl(uploadDTO.getData().getUrl());
        companyRepository.save(company);
        return new UploadDTO(uploadDTO.getData().getId(), company.getCoverUrl());
    }

    @Override
    public PageImpl<CompanyMapResponse> getMapCompany(Long regionId, String q, Boolean verified, int page, int perPage, AppLanguage language) {
        return getPublicCompanyMapPage(regionId,q,verified,page,perPage,language);
    }

    @Override
    public Optional<Company> findByIdAndDeletedAtIsNull(Long companyId) {
        return companyRepository.findByIdAndDeletedAtIsNull(companyId);
    }

    @Override
    public Company findAllByOwnerUserIdAndDeletedAtIsNull(Long sellerId) {
       return companyRepository.findAllByOwnerUserIdAndDeletedAtIsNull(sellerId);
    }

    @Override
    public Long countByVerificationStatusAndDeletedAtIsNull(VerificationStatus verificationStatus) {
        return companyRepository.countByVerificationStatusAndDeletedAtIsNull(verificationStatus);
    }

    @Override
    public List<CompanyMapResponse> findAllByIdInAndDeletedAtIsNullAndIsBlockedFalseAndLatNotNullAndLngNotNull(List<Long> companyIds) {
        List<Company> result = companyRepository.findAllByIdInAndDeletedAtIsNullAndIsBlockedFalseAndLatNotNullAndLngNotNull(companyIds);
        List<CompanyMapResponse> companyMapResponses = new LinkedList<>();
        result.forEach(company -> {
            CompanyMapResponse companyMapResponse = new CompanyMapResponse();
            companyMapResponse.setCompanyName(company.getName());
            companyMapResponse.setSlug(company.getSlug());
            companyMapResponse.setLogoUrl(company.getLogoPath());
            companyMapResponse.setCompanyAddress(company.getAddress());
            companyMapResponse.setCompanyId(company.getId());
            companyMapResponse.setLng(company.getLng());
            companyMapResponse.setLat(company.getLat());
            companyMapResponse.setVerificationStatus(company.getVerificationStatus());
            companyMapResponses.add(companyMapResponse);
        });
        return companyMapResponses;
    }

    private PageImpl<CompanyShortDTO> getPublicCompanyPage(String q, Boolean verified, Long regionId, int page, int perPage, AppLanguage language) {
        int p = normalizePage(page, language);
        int size = normalizePerPage(perPage, language);

        Specification<Company> spec = Specification.where(notDeleted()).and((r, qy, cb) -> cb.isFalse(r.get("isBlocked")));
        List<VerificationStatus> statuses;

        if (verified == null) {
            statuses = List.of(VerificationStatus.VERIFIED, VerificationStatus.PENDING_VERIFICATION);
        } else if (verified) {
            statuses = List.of(VerificationStatus.VERIFIED);
        } else {
            statuses = List.of(VerificationStatus.PENDING_VERIFICATION);
        }

        spec = spec.and((r, qy, cb) -> r.get("verificationStatus").in(statuses));

        if (StringUtils.hasText(q)) {
            String keyword = "%" + q.trim().toLowerCase() + "%";

            spec = spec.and((r, qy, cb) -> cb.or(cb.like(cb.lower(r.get("name")), keyword), cb.like(cb.lower(r.get("slug")), keyword), cb.like(cb.lower(r.get("description")), keyword)));
        }
/*
        if (categoryId != null) {
            spec=spec.and((r,qy, cb)-> cb.equal(r.get("categoryId"), categoryId));
        }*/

        if (regionId != null) {
            spec = spec.and((r, qy, cb) -> cb.equal(r.get("regionId"), regionId));
        }

        PageRequest pageable = PageRequest.of(p - 1, size, Sort.by(Sort.Direction.DESC, "createdDate"));

        Page<Company> pageResult = companyRepository.findAll(spec, pageable);

        List<CompanyShortDTO> dtoList = pageResult.getContent().stream().map(this::toShortResponse).toList();
        return new PageImpl<>(dtoList, pageable, pageResult.getTotalElements());
    }

    private PageImpl<CompanyMapResponse> getPublicCompanyMapPage(Long regionId,String q, Boolean verified, int page, int perPage, AppLanguage language) {
        int p = normalizePage(page, language);
        int size = normalizePerPage(perPage, language);

        Specification<Company> spec = Specification.where(notDeleted()).and((r, qy, cb) -> cb.isFalse(r.get("isBlocked")));
        List<VerificationStatus> statuses;

        if (verified == null) {
            statuses = List.of(VerificationStatus.VERIFIED, VerificationStatus.PENDING_VERIFICATION);
        } else if (verified) {
            statuses = List.of(VerificationStatus.VERIFIED);
        } else {
            statuses = List.of(VerificationStatus.PENDING_VERIFICATION);
        }

        spec = spec.and((r, qy, cb) -> r.get("verificationStatus").in(statuses));

        if (StringUtils.hasText(q)) {
            String keyword = "%" + q.trim().toLowerCase() + "%";

            spec = spec.and((r, qy, cb) -> cb.or(cb.like(cb.lower(r.get("name")), keyword), cb.like(cb.lower(r.get("slug")), keyword), cb.like(cb.lower(r.get("description")), keyword)));
        }

        if (regionId != null) {
            spec = spec.and((r, qy, cb) -> cb.equal(r.get("regionId"), regionId));
        }

        PageRequest pageable = PageRequest.of(p - 1, size, Sort.by(Sort.Direction.DESC, "createdDate"));

        Page<Company> pageResult = companyRepository.findAll(spec, pageable);

        List<CompanyMapResponse> dtoList = pageResult.getContent().stream().map(this::toCompanyMapResponse).toList();
        return new PageImpl<>(dtoList, pageable, pageResult.getTotalElements());
    }

    private Company findOwnedCompany(Long id, AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        return companyRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(id, profileId).orElseThrow(() -> new AppBadException(messageService.getMessage("company.not.found", language)));
    }
    private CompanySlugMapResponse toSlugMapResponse(Company company) {
        CompanySlugMapResponse response = new CompanySlugMapResponse();
        response.setId(company.getId());
        response.setName(company.getName());
        response.setSlug(company.getSlug());
        response.setStatus(company.getVerificationStatus());
        response.setAddress(company.getAddress());
        response.setLng(company.getLng());
        response.setLat(company.getLat());
        return response;
    }

    private CompanyResponseDTO toResponse(Company company) {
        CompanyResponseDTO response = new CompanyResponseDTO();
        response.setId(company.getId());
        response.setName(company.getName());
        response.setSlug(company.getSlug());
        response.setShortDescription(company.getShortDescription());
        response.setDescription(company.getDescription());
        response.setLogoUrl(company.getLogoPath());
        response.setCoverUrl(company.getCoverUrl());
        response.setStir(company.getStir());
        response.setPhonePrimary(company.getPhonePrimary());
        response.setPhoneSecondary(company.getPhoneSecondary());
        response.setWebsite(company.getWebsite());
        response.setAddress(company.getAddress());
        response.setVerificationStatus(company.getVerificationStatus());
        response.setVerifiedAt(company.getVerifiedAt());
        response.setCreatedAt(company.getCreatedDate());
        return response;
    }

    private CompanyMapResponse toCompanyMapResponse(Company company) {
        CompanyMapResponse response = new CompanyMapResponse();
        response.setCompanyId(company.getId());
        response.setCompanyName(company.getName());
        response.setSlug(company.getSlug());
        response.setCompanyAddress(company.getAddress());
        response.setLogoUrl(company.getLogoPath());
        response.setVerificationStatus(company.getVerificationStatus());
        response.setLng(company.getLng());
        response.setLat(company.getLat());
        return response;
    }


    private CompanyShortDTO toShortResponse(Company company) {
        CompanyShortDTO response = new CompanyShortDTO();
        response.setId(company.getId());
        response.setName(company.getName());
        response.setSlug(company.getSlug());
        response.setLogoUrl(company.getLogoPath());
        response.setVerificationStatus(company.getVerificationStatus());
        response.setIsBlocked(company.getIsBlocked());
        response.setCreatedAt(company.getCreatedDate());
        return response;
    }
    private CompanyInfoDTO toInfoResponse(Company company) {
        CompanyInfoDTO response = new CompanyInfoDTO();
        response.setId(company.getId());
        response.setName(company.getName());
        response.setSlug(company.getSlug());
        response.setLogoUrl(company.getLogoPath());
        response.setVerificationStatus(company.getVerificationStatus());
        response.setIsBlocked(company.getIsBlocked());
        response.setCompanyCreatedDate(company.getCompanyCreatedDate());
        return response;
    }

    private Specification<Company> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    private String generateSlug(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private int normalizePage(int page, AppLanguage language) {
        if (page < 1) {
            throw new AppBadException(messageService.getMessage("page.invalid", language));
        }
        return page;
    }

    private int normalizePerPage(int perPage, AppLanguage language) {
        if (perPage < 1 || perPage > 100) {
            throw new AppBadException(messageService.getMessage("per.page.invalid", language));
        }
        return perPage;
    }
}
