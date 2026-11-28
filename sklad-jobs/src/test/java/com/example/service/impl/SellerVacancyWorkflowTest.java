package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.document.VacancyDocument;
import com.example.dto.vacancy.VacancyCreate;
import com.example.dto.vacancy.VacancyRequest;
import com.example.entity.Vacancy;
import com.example.enums.AppLanguage;
import com.example.enums.VacancyStatus;
import com.example.exp.AppBadException;
import com.example.repository.VacancyRepository;
import com.example.service.JobCategoryService;
import com.example.service.ResourceBundleService;
import com.example.service.VacancySearchService;
import com.example.utils.SpringSecurityUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SellerVacancyWorkflowTest {
    private final VacancyRepository repository = mock(VacancyRepository.class);
    private final CompanyClient companies = mock(CompanyClient.class);
    private final VacancySearchService search = mock(VacancySearchService.class);
    private final JobCategoryService categories = mock(JobCategoryService.class);
    private final VacancyServiceImpl service = new VacancyServiceImpl(repository, companies, search,
            new ModelMapper(), mock(ResourceBundleService.class), categories);

    @Test
    void createEntersModerationImmediatelyInDatabaseAndSearchIndex() {
        var request = new VacancyCreate();
        request.setCompanyId(3L);
        request.setPositionName("Operator");
        when(companies.getOwnedCompanyIds(7L)).thenReturn(List.of(3L));
        when(repository.save(any(Vacancy.class))).thenAnswer(call -> {
            Vacancy vacancy = call.getArgument(0);
            assertEquals(VacancyStatus.UNDER_MODERATION, vacancy.getVacancyStatus());
            assertNull(vacancy.getPublishedAt());
            vacancy.setId(42L);
            return vacancy;
        });
        try (var auth = mockStatic(SpringSecurityUtil.class)) {
            auth.when(SpringSecurityUtil::getProfileId).thenReturn(7L);
            assertEquals("Operator", service.createVacancy(request, AppLanguage.UZ).getPositionName());
        }
        var document = ArgumentCaptor.forClass(VacancyDocument.class);
        verify(search).index(document.capture());
        assertEquals("42", document.getValue().getId());
        assertEquals(VacancyStatus.UNDER_MODERATION, document.getValue().getVacancyStatus());
        assertNull(document.getValue().getPublishedAt());
    }

    @ParameterizedTest
    @EnumSource(VacancyStatus.class)
    void directEditResubmitsForModerationAndRemovesPublicationFromSearch(VacancyStatus status) {
        var vacancy = vacancy(status);
        when(repository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(vacancy));
        when(companies.getOwnedCompanyIds(7L)).thenReturn(List.of(3L));
        when(repository.save(vacancy)).thenReturn(vacancy);
        var request = new VacancyRequest();
        request.setPositionName("Updated operator");
        try (var auth = mockStatic(SpringSecurityUtil.class)) {
            auth.when(SpringSecurityUtil::getProfileId).thenReturn(7L);
            service.updateVacancy(request, 42L, AppLanguage.UZ);
        }
        assertEquals(VacancyStatus.UNDER_MODERATION, vacancy.getVacancyStatus());
        assertNull(vacancy.getPublishedAt());
        assertEquals(12L, vacancy.getViewsCountCache());
        verify(search).update(argThat(document -> document.getVacancyStatus() == VacancyStatus.UNDER_MODERATION
                && "Updated operator".equals(document.getPositionName())
                && document.getPublishedAt() == null));
        verify(repository, times(1)).save(vacancy);
    }

    @Test
    void anotherSellersVacancyCannotBeChangedOrReindexed() {
        var vacancy = vacancy(VacancyStatus.PUBLISHED);
        when(repository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(vacancy));
        when(companies.getOwnedCompanyIds(7L)).thenReturn(List.of(99L));
        var request = new VacancyRequest();
        request.setPositionName("Unowned change");
        try (var auth = mockStatic(SpringSecurityUtil.class)) {
            auth.when(SpringSecurityUtil::getProfileId).thenReturn(7L);
            assertThrows(AppBadException.class, () -> service.updateVacancy(request, 42L, AppLanguage.UZ));
        }
        assertEquals("Operator", vacancy.getPositionName());
        verify(repository, never()).save(any());
        verifyNoInteractions(search, categories);
    }

    @Test
    void editRequiresAuthenticatedSellerProfile() {
        try (var auth = mockStatic(SpringSecurityUtil.class)) {
            auth.when(SpringSecurityUtil::getProfileId).thenReturn(null);
            assertThrows(AccessDeniedException.class,
                    () -> service.updateVacancy(new VacancyRequest(), 42L, AppLanguage.UZ));
        }
        verifyNoInteractions(repository, companies, search, categories);
    }

    private Vacancy vacancy(VacancyStatus status) {
        var vacancy = new Vacancy();
        vacancy.setId(42L);
        vacancy.setCompanyId(3L);
        vacancy.setPositionName("Operator");
        vacancy.setVacancyStatus(status);
        vacancy.setViewsCountCache(12L);
        if (status == VacancyStatus.PUBLISHED) vacancy.setPublishedAt(Instant.parse("2026-10-01T10:00:00Z"));
        return vacancy;
    }
}
