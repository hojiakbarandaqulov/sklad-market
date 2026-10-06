package com.example.service.impl;

import com.example.dto.ApiResponse;
import com.example.dto.vacancy.VacancyDTO;
import com.example.dto.vacancy.VacancyRequest;
import com.example.entity.Vacancy;
import com.example.enums.AppLanguage;
import com.example.enums.VacancyStatus;
import com.example.exp.AppBadException;
import com.example.repository.VacancyRepository;
import com.example.service.BuyerVacancyService;
import com.example.service.ResourceBundleService;
import com.example.utils.SpringSecurityUtil;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BuyerVacancyServiceImpl implements BuyerVacancyService {
    private final VacancyRepository repository;
    private final ModelMapper modelMapper;
    private final ResourceBundleService messages;

    @Override
    public VacancyDTO update(Long id, VacancyRequest request, AppLanguage language) {
        Vacancy vacancy = findOwned(id, language);
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
        vacancy.setPublishedAt(null);
        return modelMapper.map(repository.save(vacancy), VacancyDTO.class);
    }

    @Override
    public ApiResponse<String> close(Long id, AppLanguage language) {
        return changeStatus(id, VacancyStatus.CLOSED, "vacancy.close.success", language);
    }

    @Override
    public ApiResponse<String> archive(Long id, AppLanguage language) {
        return changeStatus(id, VacancyStatus.ARCHIVE, "vacancy.archive.success", language);
    }

    @Override
    public ApiResponse<String> submit(Long id, AppLanguage language) {
        return changeStatus(id, VacancyStatus.UNDER_MODERATION, "vacancy.submit.success", language);
    }

    private ApiResponse<String> changeStatus(Long id, VacancyStatus status, String message, AppLanguage language) {
        Vacancy vacancy = findOwned(id, language);
        vacancy.setVacancyStatus(status);
        repository.save(vacancy);
        return ApiResponse.successResponse(messages.getMessage(message, language));
    }

    private Vacancy findOwned(Long id, AppLanguage language) {
        Long profileId = SpringSecurityUtil.getProfileId();
        if (profileId == null || profileId <= 0) {
            throw new AccessDeniedException("Authentication required");
        }
        if (id == null || id <= 0) {
            throw new AppBadException("Vacancy ID must be positive");
        }
        Vacancy vacancy = repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new AppBadException(messages.getMessage("vacancy.not.found", language)));
        if (!profileId.equals(vacancy.getBuyerId()) || vacancy.getCompanyId() != null) {
            throw new AccessDeniedException("Buyer vacancy access denied");
        }
        return vacancy;
    }
}