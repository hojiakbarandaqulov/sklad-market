package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.document.VacancyDocument;
import com.example.dto.ApiResponse;
import com.example.dto.vacancy.*;
import com.example.exp.AppNotFoundException;
import com.example.repository.specification.VacancySpecifications;
import com.example.service.VacancySearchService;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;

import java.util.HashMap;
import java.util.Map;

import com.example.entity.Vacancy;
import com.example.enums.AppLanguage;
import com.example.enums.VacancyModeration;
import com.example.enums.VacancyStatus;
import com.example.exp.AppBadException;
import com.example.repository.VacancyRepository;
import com.example.service.ResourceBundleService;
import com.example.service.VacancyService;
import com.example.utils.SpringSecurityUtil;
import feign.Client;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class VacancyServiceImpl implements VacancyService {

    private final VacancyRepository vacancyRepository;
    private final CompanyClient companyClient;
    private final VacancySearchService vacancySearchService;
    private final ModelMapper modelMapper;
    private final ResourceBundleService messageService;

    @Override
    public VacancyCreate createVacancy(VacancyCreate vacancyCreate, AppLanguage language) {
        Long profileId = requireSellerProfile(language);

        List<Long> ownedCompanyIds = companyClient.getOwnedCompanyIds(profileId);
        if (ownedCompanyIds == null || !ownedCompanyIds.contains(vacancyCreate.getCompanyId())) {
            throw new AppBadException(
                    messageService.getMessage("vacancy.company.access.denied", language)
            );
        }

        Vacancy savedVacancy = vacancyRepository.save(toEntity(vacancyCreate));
        vacancySearchService.index(toDocument(savedVacancy));
        return modelMapper.map(savedVacancy, VacancyCreate.class);
    }

    @Override
    public VacancyCreate updateVacancy(VacancyRequest vacancyUpdate, Long vacancyId, AppLanguage language) {
        Optional<Vacancy> vacancy = vacancyRepository.findByIdAndDeletedFalse(vacancyId);
        if (vacancy.isEmpty()) {
            throw new AppBadException(messageService.getMessage("vacancy.not.found", language));
        }
        Vacancy vacancyEntity = vacancy.get();
        vacancyEntity.setPositionName(vacancyUpdate.getPositionName());
        vacancyEntity.setPrice(vacancyUpdate.getPrice());
        vacancyEntity.setEmploymentType(vacancyUpdate.getEmploymentType());
        vacancyEntity.setWorkSchedule(vacancyUpdate.getWorkSchedule());
        vacancyEntity.setShortDescription(vacancyUpdate.getShortDescription());
        vacancyEntity.setRegionId(vacancyUpdate.getRegionId());
        vacancyEntity.setAddress(vacancyUpdate.getAddress());
        vacancyEntity.setVacancyStatus(VacancyStatus.DRAFT);
        vacancyEntity.setLng(vacancyUpdate.getLng());
        vacancyEntity.setLat(vacancyUpdate.getLat());
        vacancyEntity.setExperienceLevel(vacancyUpdate.getExperienceLevel());
        vacancyEntity.setRequirements(vacancyUpdate.getRequirements());
        vacancyEntity.setWorkingConditions(vacancyUpdate.getWorkingConditions());
        vacancyEntity.setShowContacts(Boolean.TRUE.equals(vacancyUpdate.getShowContacts()));
        vacancyEntity.setVacancyStatus(VacancyStatus.DRAFT);
        Vacancy save = vacancyRepository.save(vacancyEntity);
        vacancySearchService.update(toDocument(save));
        return modelMapper.map(save, VacancyCreate.class);
    }

    @Override
    public PageImpl<VacancyDTO> getCompanyVacancy(Long companyId, int page, int perPage, AppLanguage language) {
        Long profileId = requireSellerProfile(language);
        List<Long> ownedCompanyIds = companyClient.getOwnedCompanyIds(profileId);
        if (ownedCompanyIds == null || !ownedCompanyIds.contains(companyId)) {
            throw new AppBadException(messageService.getMessage("company.not.found", language));
        }
        Pageable pageable = PageRequest.of(page - 1, perPage);
        PageImpl<Vacancy> vacancy = vacancyRepository.findAllByCompanyIdAndDeletedFalse(companyId, pageable);
        List<VacancyDTO> items = vacancy.getContent()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(items, PageRequest.of(page - 1, perPage), vacancy.getTotalElements());
    }

    @Override
    public ApiResponse<String> submitVacancyModeration(Long vacancyId, AppLanguage language) {
        Long profileId = requireSellerProfile(language);
        Optional<Vacancy> vacancyOptional = vacancyRepository.findByIdAndDeletedFalse(vacancyId);
        if (vacancyOptional.isEmpty()) {
            throw new AppBadException(messageService.getMessage("vacancy.not.found", language));
        }
        Vacancy vacancy = vacancyOptional.get();
        List<Long> ownedCompanyIds = companyClient.getOwnedCompanyIds(profileId);
        if (ownedCompanyIds == null || !ownedCompanyIds.contains(vacancy.getCompanyId())) {
            throw new AppBadException(messageService.getMessage("company.not.found", language));
        }
        vacancy.setVacancyStatus(VacancyStatus.UNDER_MODERATION);
        vacancyRepository.save(vacancy);
        return ApiResponse.successResponse(messageService.getMessage("vacancy.submit.success", language));
    }

    @Override
    public ApiResponse<String> closeVacancy(Long vacancyId, AppLanguage language) {
        Long profileId = requireSellerProfile(language);
        Optional<Vacancy> vacancyOptional = vacancyRepository.findByIdAndDeletedFalse(vacancyId);
        if (vacancyOptional.isEmpty()) {
            throw new AppBadException(messageService.getMessage("vacancy.not.found", language));
        }
        Vacancy vacancy = vacancyOptional.get();
        List<Long> ownedCompanyIds = companyClient.getOwnedCompanyIds(profileId);
        if (ownedCompanyIds == null || !ownedCompanyIds.contains(vacancy.getCompanyId())) {
            throw new AppBadException(messageService.getMessage("company.not.found", language));
        }
        vacancy.setVacancyStatus(VacancyStatus.CLOSED);
        vacancyRepository.save(vacancy);
        return ApiResponse.successResponse(messageService.getMessage("vacancy.close.success", language));
    }

    @Override
    public ApiResponse<String> archiveVacancy(Long vacancyId, AppLanguage language) {
        Long profileId = requireSellerProfile(language);
        Optional<Vacancy> vacancyOptional = vacancyRepository.findByIdAndDeletedFalse(vacancyId);
        if (vacancyOptional.isEmpty()) {
            throw new AppBadException(messageService.getMessage("vacancy.not.found", language));
        }
        Vacancy vacancy = vacancyOptional.get();
        List<Long> ownedCompanyIds = companyClient.getOwnedCompanyIds(profileId);
        if (ownedCompanyIds == null || !ownedCompanyIds.contains(vacancy.getCompanyId())) {
            throw new AppBadException(messageService.getMessage("company.not.found", language));
        }
        vacancy.setVacancyStatus(VacancyStatus.ARCHIVE);
        vacancyRepository.save(vacancy);
        return ApiResponse.successResponse(messageService.getMessage("vacancy.archive.success", language));
    }

    @Override
    public PageImpl<VacancyDTO> getModerationQueue(int page, int perPage, AppLanguage language) {
        if (page < 1 || perPage < 1 || perPage > 100) {
            throw new AppBadException(messageService.getMessage("vacancy.filter.invalid", language));
        }
        Pageable pageable = PageRequest.of(page - 1, perPage, Sort.by("id").ascending());
        Page<Vacancy> vacancies = vacancyRepository.findAllByVacancyStatusAndDeletedFalse(
                VacancyStatus.UNDER_MODERATION, pageable);
        return new PageImpl<>(vacancies.getContent().stream().map(this::toDTO).toList(),
                pageable, vacancies.getTotalElements());
    }


    @Override
    public ApiResponse<String> vacancyModeration(Long vacancyId, VacancyModeration vacancyModeration, VacancyModerationComment vacancyModerationComment, AppLanguage language) {
        Optional<Vacancy> vacancyOptional = vacancyRepository.findByIdAndDeletedFalse(vacancyId);
        if (vacancyOptional.isEmpty()) {
            throw new AppBadException(messageService.getMessage("vacancy.not.found", language));
        }
        Vacancy vacancy = vacancyOptional.get();

        VacancyStatus status = switch (vacancyModeration) {
            case REJECTED -> VacancyStatus.REJECTED;
            case PUBLISHED -> VacancyStatus.PUBLISHED;
        };
        vacancy.setVacancyStatus(status);
        vacancy.setComment(vacancyModerationComment.getComment());
        vacancyRepository.save(vacancy);
        return ApiResponse.successResponse(messageService.getMessage("vacancy.moderation.success", language));
    }

    @Override
    public PageImpl<PublicVacancyDTO> getVacancies(VacancyFilter filter, AppLanguage language) {
        if (filter.getPage() < 1 || filter.getPerPage() < 1 || filter.getPerPage() > 100 || !filter.isSalaryRangeValid())
            throw new AppBadException(messageService.getMessage("vacancy.filter.invalid", language));

        Pageable pageable = PageRequest.of(
                filter.getPage() - 1,
                filter.getPerPage(),
                Sort.by(Sort.Order.desc("publishedAt")
                )
        );
        Page<VacancyDocument> page =
                vacancySearchService.search(filter, pageable);

        Map<Long, CompanySummaryDTO> companies = new HashMap<>();

        var items = page.getContent().stream()
                .map(document -> {
                    PublicVacancyDTO dto = modelMapper.map(
                            document,
                            PublicVacancyDTO.class
                    );

                    dto.setId(Long.valueOf(document.getId()));
                    dto.setViewsCount(document.getViewsCountCache());
                    dto.setContacts(null);

                    if (document.getCompanyId() != null) {
                        CompanySummaryDTO company = companies.computeIfAbsent(
                                document.getCompanyId(),
                                companyClient::getSummary
                        );

                        enrichCompany(dto, company);
                    }

                    return dto;
                })
                .toList();

        return new PageImpl<>(
                items,
                pageable,
                page.getTotalElements()
        );
    }

    @Transactional
    @Override
    public PublicVacancyDTO getVacancy(Long id, AppLanguage language) {
        if (id == null || id <= 0) throw new AppBadException(messageService.getMessage("vacancy.id.invalid", language));
        int i = vacancyRepository.incrementCount(id, VacancyStatus.PUBLISHED);
        Vacancy vacancy = vacancyRepository.findByIdAndVacancyStatusAndDeletedFalse(id, VacancyStatus.PUBLISHED)
                .orElseThrow(() -> new AppNotFoundException(messageService.getMessage("vacancy.not.found", language)));
        PublicVacancyDTO dto = toPublicDTO(vacancy);
        if (vacancy.getCompanyId() != null) {
            CompanySummaryDTO company = companyClient.getSummary(vacancy.getCompanyId());
            enrichCompany(dto, company);
            if (Boolean.TRUE.equals(vacancy.getShowContacts()) && company != null && company.getSlug() != null) {
                var response = companyClient.getPublicContacts(company.getSlug(), language.name());
                if (response != null && Boolean.TRUE.equals(response.getSuccess())) dto.setContacts(response.getData());
            }
        }
        return dto;
    }

    @Override
    public VacancyCreateBuyerResponseDTO createVacancyBuyer(VacancyCreateBuyer vacancyDTO, AppLanguage language) {
        Vacancy vacancy = toEntityBuyer(vacancyDTO);
        vacancy.setBuyerId(requireSellerProfile(language));
        Vacancy savedVacancy = vacancyRepository.save(vacancy);

        return modelMapper.map(savedVacancy, VacancyCreateBuyerResponseDTO.class);
    }

    @Override
    public PageImpl<VacancyDTO> getMyVacancy(int page, int perPage, AppLanguage language) {
        Long profileId = requireSellerProfile(language);
        if (page < 1 || perPage < 1 || perPage > 100) {
            throw new AppBadException(messageService.getMessage("vacancy.filter.invalid", language));
        }
        Pageable pageable = PageRequest.of(page - 1, perPage, Sort.by(Sort.Order.desc("createdDate"), Sort.Order.desc("id")));

        Page<Vacancy> vacancies = vacancyRepository.findByBuyerIdAndDeletedFalse(profileId, pageable);

        return new PageImpl<>(vacancies.getContent().stream().map(this::toDTO).toList(),
                pageable, vacancies.getTotalElements());
    }

    @Override
    public ApiResponse<String> archiveExtVacancy(Long vacancyId, AppLanguage language) {
        Optional<Vacancy> vacancyOptional = vacancyRepository.findByIdAndDeletedFalse(vacancyId);
        if (vacancyOptional.isEmpty()) {
            throw new AppBadException(messageService.getMessage("vacancy.not.found", language));
        }
        Vacancy vacancy = vacancyOptional.get();
        vacancy.setVacancyStatus(VacancyStatus.DRAFT);
        vacancyRepository.save(vacancy);
        return ApiResponse.successResponse(messageService.getMessage("vacancy.archive.success", language));
    }

    private Vacancy toEntityBuyer(VacancyCreateBuyer request) {
        Vacancy vacancy = new Vacancy();
        vacancy.setPositionName(request.getPositionName());
        vacancy.setPrice(request.getPrice());
        vacancy.setEmploymentType(request.getEmploymentType());
        vacancy.setWorkSchedule(request.getWorkSchedule());
        vacancy.setShortDescription(request.getShortDescription());
        vacancy.setRegionId(request.getRegionId());
        vacancy.setAddress(request.getAddress());
        vacancy.setVacancyStatus(VacancyStatus.DRAFT);
        vacancy.setLng(request.getLng());
        vacancy.setLat(request.getLat());
        vacancy.setExperienceLevel(request.getExperienceLevel());
        vacancy.setRequirements(request.getRequirements());
        vacancy.setWorkingConditions(request.getWorkingConditions());
        vacancy.setShowContacts(Boolean.TRUE.equals(request.getShowContacts()));
        return vacancy;
    }

    private PublicVacancyDTO toPublicDTO(Vacancy vacancy) {
        PublicVacancyDTO dto = modelMapper.map(vacancy, PublicVacancyDTO.class);
        dto.setId(vacancy.getId());
        dto.setViewsCount(vacancy.getViewsCountCache());
        dto.setContacts(null);
        return dto;
    }

    private void enrichCompany(PublicVacancyDTO dto, CompanySummaryDTO company) {
        if (company != null) {
            dto.setCompanyName(company.getName());
            dto.setCompanyLogo(company.getLogoPath());
        }
    }

    private Vacancy toEntity(VacancyCreate request) {
        Vacancy vacancy = new Vacancy();
        vacancy.setCompanyId(request.getCompanyId());
        vacancy.setPositionName(request.getPositionName());
        vacancy.setPrice(request.getPrice());
        vacancy.setEmploymentType(request.getEmploymentType());
        vacancy.setWorkSchedule(request.getWorkSchedule());
        vacancy.setShortDescription(request.getShortDescription());
        vacancy.setRegionId(request.getRegionId());
        vacancy.setAddress(request.getAddress());
        vacancy.setVacancyStatus(VacancyStatus.DRAFT);
        vacancy.setLng(request.getLng());
        vacancy.setLat(request.getLat());
        vacancy.setExperienceLevel(request.getExperienceLevel());
        vacancy.setRequirements(request.getRequirements());
        vacancy.setWorkingConditions(request.getWorkingConditions());
        vacancy.setShowContacts(Boolean.TRUE.equals(request.getShowContacts()));
        return vacancy;
    }

    private Vacancy toEntityUpdate(VacancyRequest request) {
        Vacancy vacancy = new Vacancy();
        vacancy.setPositionName(request.getPositionName());
        vacancy.setPrice(request.getPrice());
        vacancy.setEmploymentType(request.getEmploymentType());
        vacancy.setWorkSchedule(request.getWorkSchedule());
        vacancy.setShortDescription(request.getShortDescription());
        vacancy.setRegionId(request.getRegionId());
        vacancy.setAddress(request.getAddress());
        vacancy.setLng(request.getLng());
        vacancy.setLat(request.getLat());
        vacancy.setExperienceLevel(request.getExperienceLevel());
        vacancy.setRequirements(request.getRequirements());
        vacancy.setWorkingConditions(request.getWorkingConditions());
        vacancy.setShowContacts(Boolean.TRUE.equals(request.getShowContacts()));
        vacancy.setVacancyStatus(VacancyStatus.DRAFT);
        return vacancy;
    }

    private VacancyDTO toDTO(Vacancy vacancy) {
        return modelMapper.map(vacancy, VacancyDTO.class);
    }


    private Long requireSellerProfile(AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null) {
            throw new AccessDeniedException(
                    messageService.getMessage("auth.seller.profile.required", language)
            );
        }
        return profileId;
    }

    public VacancyDocument toDocument(Vacancy vacancy) {
        Assert.notNull(vacancy, "Vacancy must not be null");
        Assert.isTrue(
                vacancy.getId() != null && vacancy.getId() > 0,
                "Vacancy must be saved in the database before indexing"
        );

        return VacancyDocument.builder()
                .id(vacancy.getId().toString())
                .vacancyId(vacancy.getId())
                .positionName(vacancy.getPositionName())
                .companyId(vacancy.getCompanyId())
                .buyerId(vacancy.getBuyerId())
                .price(vacancy.getPrice())
                .employmentType(vacancy.getEmploymentType())
                .workSchedule(vacancy.getWorkSchedule())
                .shortDescription(vacancy.getShortDescription())
                .viewsCountCache(
                        vacancy.getViewsCountCache() == null
                                ? 0L
                                : vacancy.getViewsCountCache()
                )
                .vacancyStatus(vacancy.getVacancyStatus())
                .comment(vacancy.getComment())
                .regionId(vacancy.getRegionId())
                .address(vacancy.getAddress())
                .lng(toCoordinate(vacancy.getLng(), "lng"))
                .lat(toCoordinate(vacancy.getLat(), "lat"))
                .experienceLevel(vacancy.getExperienceLevel())
                .requirements(vacancy.getRequirements())
                .workingConditions(vacancy.getWorkingConditions())
                .publishedAt(vacancy.getPublishedAt())
                .deleted(Boolean.TRUE.equals(vacancy.getDeleted()))
                .showContacts(Boolean.TRUE.equals(vacancy.getShowContacts()))
                .build();
    }

    public PublicVacancyDTO toPublicDTO(VacancyDocument document) {
        Assert.notNull(document, "Vacancy document must not be null");

        PublicVacancyDTO dto = new PublicVacancyDTO();

        dto.setId(Long.valueOf(document.getId()));
        dto.setCompanyId(document.getCompanyId());
        dto.setPositionName(document.getPositionName());
        dto.setPrice(document.getPrice());
        dto.setEmploymentType(document.getEmploymentType());
        dto.setWorkSchedule(document.getWorkSchedule());
        dto.setShortDescription(document.getShortDescription());
        dto.setVacancyStatus(document.getVacancyStatus());
        dto.setRegionId(document.getRegionId());
        dto.setAddress(document.getAddress());
        dto.setLng(toCoordinateText(document.getLng()));
        dto.setLat(toCoordinateText(document.getLat()));
        dto.setExperienceLevel(document.getExperienceLevel());
        dto.setRequirements(document.getRequirements());
        dto.setComment(document.getComment());
        dto.setWorkingConditions(document.getWorkingConditions());
        dto.setShowContacts(Boolean.TRUE.equals(document.getShowContacts()));
        dto.setPublishedAt(document.getPublishedAt());
        dto.setViewsCount(
                document.getViewsCountCache() == null
                        ? 0L
                        : document.getViewsCountCache()
        );

        // Kontaktlar alohida detail API orqali olinadi.
        dto.setContacts(null);

        // companyName va companyLogo keyin CompanyClient orqali qo‘shiladi.
        return dto;
    }

    private Double toCoordinate(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            double coordinate = Double.parseDouble(value.trim());

            if (!Double.isFinite(coordinate)) {
                throw new IllegalArgumentException(
                        fieldName + " must be a finite number"
                );
            }

            return coordinate;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    fieldName + " must be a number: " + value,
                    exception
            );
        }
    }

    private String toCoordinateText(Double value) {
        return value == null ? null : value.toString();
    }
}