package com.example.service.impl;

import com.example.dto.favorite.*;
import com.example.dto.vacancy.VacancyDTO;
import com.example.entity.VacancyFavorite;
import com.example.enums.AppLanguage;
import com.example.enums.VacancyStatus;
import com.example.exp.AppBadException;
import com.example.repository.VacancyFavoriteRepository;
import com.example.repository.VacancyRepository;
import com.example.service.ResourceBundleService;
import com.example.service.VacancyFavoriteService;
import com.example.utils.SpringSecurityUtil;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacancyFavoriteServiceImpl implements VacancyFavoriteService {
    private final VacancyFavoriteRepository favorites;
    private final VacancyRepository vacancies;
    private final ModelMapper mapper;
    private final ResourceBundleService messages;

    @Override
    public PageImpl<VacancyDTO> getFavorites(int page, int perPage, AppLanguage language) {
        Long userId = requireProfile();
        if (page < 1 || perPage < 1 || perPage > 100) {
            throw new AppBadException(messages.getMessage("vacancy.filter.invalid", language));
        }
        var pageable = PageRequest.of(page - 1, perPage);
        var result = favorites.findVisibleFavorites(userId, VacancyStatus.PUBLISHED, pageable);
        return new PageImpl<>(result.getContent().stream()
                .map(v -> mapper.map(v, VacancyDTO.class)).toList(), pageable, result.getTotalElements());
    }

    @Override
    public VacancyFavoriteCountResponse getCount(AppLanguage language) {
        return new VacancyFavoriteCountResponse(
                favorites.countVisibleFavorites(requireProfile(), VacancyStatus.PUBLISHED));
    }

    @Override
    @Transactional
    public VacancyFavoriteResponse add(Long vacancyId, AppLanguage language) {
        Long userId = requireProfile();
        validateId(vacancyId);
        // Lock the vacancy so concurrent saves cannot create duplicate favorites.
        var vacancy = vacancies.findForApplication(vacancyId)
                .orElseThrow(() -> new AppBadException(messages.getMessage("vacancy.not.found", language)));
        if (vacancy.getVacancyStatus() != VacancyStatus.PUBLISHED) {
            throw new AppBadException(messages.getMessage("vacancy.not.found", language));
        }
        var favorite = favorites.findByUserIdAndVacancyId(userId, vacancyId).orElseGet(() -> {
            var entity = new VacancyFavorite();
            entity.setUserId(userId);
            entity.setVacancyId(vacancyId);
            return entity;
        });
        favorite.setDeleted(false);
        favorite.setIsActive(true);
        favorites.save(favorite);
        return new VacancyFavoriteResponse(true);
    }

    @Override
    @Transactional
    public VacancyFavoriteResponse remove(Long vacancyId, AppLanguage language) {
        Long userId = requireProfile();
        validateId(vacancyId);
        if (favorites.deactivate(userId, vacancyId) == 0) {
            throw new AppBadException(messages.getMessage("vacancy.favorite.not.found", language));
        }
        return new VacancyFavoriteResponse(false);
    }

    private Long requireProfile() {
        Long userId = SpringSecurityUtil.getProfileId();
        if (userId == null || userId <= 0) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return userId;
    }

    private void validateId(Long vacancyId) {
        if (vacancyId == null || vacancyId <= 0) {
            throw new AppBadException("Vacancy ID must be positive");
        }
    }
}