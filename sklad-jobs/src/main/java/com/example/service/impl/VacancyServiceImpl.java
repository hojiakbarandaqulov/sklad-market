package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.dto.ApiResponse;
import com.example.dto.vacancy.VacancyCreate;
import com.example.dto.vacancy.VacancyDTO;
import com.example.dto.vacancy.VacancyRequest;
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class VacancyServiceImpl implements VacancyService {

    private final VacancyRepository vacancyRepository;
    private final CompanyClient companyClient;
    private final ModelMapper modelMapper;
    private final ResourceBundleService messageService;

    @Override
    public VacancyDTO createVacancy(VacancyCreate vacancyCreate, AppLanguage language) {
        Long profileId = requireSellerProfile(language);

        List<Long> ownedCompanyIds = companyClient.getOwnedCompanyIds(profileId);
        if (ownedCompanyIds == null || !ownedCompanyIds.contains(vacancyCreate.getCompanyId())) {
            throw new AppBadException(
                    messageService.getMessage("vacancy.company.access.denied", language)
            );
        }

        Vacancy savedVacancy = vacancyRepository.save(toEntity(vacancyCreate));
        return modelMapper.map(savedVacancy, VacancyDTO.class);
    }

    @Override
    public VacancyDTO updateVacancy(VacancyRequest vacancyUpdate, Long vacancyId, AppLanguage language) {
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
        vacancyEntity.setResponsibilities(vacancyUpdate.getResponsibilities());
        vacancyEntity.setRequirements(vacancyUpdate.getRequirements());
        vacancyEntity.setWorkingConditions(vacancyUpdate.getWorkingConditions());
        vacancyEntity.setShowContacts(Boolean.TRUE.equals(vacancyUpdate.getShowContacts()));
        vacancyEntity.setVacancyStatus(VacancyStatus.DRAFT);
        Vacancy save = vacancyRepository.save(vacancyEntity);
        return modelMapper.map(save, VacancyDTO.class);
    }

    @Override
    public PageImpl<VacancyDTO> getCompanyVacancy(Long companyId, int page, int perPage, AppLanguage language) {
        Long profileId = requireSellerProfile(language);
        List<Long> ownedCompanyIds = companyClient.getOwnedCompanyIds(profileId);
        if (ownedCompanyIds == null || !ownedCompanyIds.contains(companyId)) {
            throw new AppBadException(messageService.getMessage("company.not.found", language));
        }
        Pageable pageable= PageRequest.of(page-1,perPage);
        PageImpl<Vacancy> vacancy = vacancyRepository.findAllByCompanyIdAndDeletedFalse(companyId,pageable);
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
    public ApiResponse<String> vacancyModeration(Long vacancyId, VacancyModeration vacancyModeration, AppLanguage language) {
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
        vacancyRepository.save(vacancy);
        return ApiResponse.successResponse(messageService.getMessage("vacancy.moderation.success", language));
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
        vacancy.setResponsibilities(request.getResponsibilities());
        vacancy.setRequirements(request.getRequirements());
        vacancy.setWorkingConditions(request.getWorkingConditions());
        vacancy.setShowContacts(Boolean.TRUE.equals(request.getShowContacts()));
        vacancy.setVacancyStatus(VacancyStatus.DRAFT);
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
        vacancy.setResponsibilities(request.getResponsibilities());
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

}