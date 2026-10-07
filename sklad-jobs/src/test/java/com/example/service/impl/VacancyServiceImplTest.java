/*
package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.dto.vacancy.VacancyCreate;
import com.example.dto.vacancy.VacancyDTO;
import com.example.dto.vacancy.VacancyRequest;
import com.example.entity.Vacancy;
import com.example.enums.AppLanguage;
import com.example.enums.VacancyStatus;
import com.example.repository.VacancyRepository;
import com.example.utils.SpringSecurityUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VacancyServiceImplTest {
    private final VacancyRepository repository = mock(VacancyRepository.class);
    private final CompanyClient companyClient = mock(CompanyClient.class);
    private final VacancyServiceImpl service = new VacancyServiceImpl(repository, companyClient, new ModelMapper());

    @Test
    void savesOwnedCompanyVacancyAsDraftEvenWhenPublishedIsRequested() {
        VacancyCreate request = new VacancyCreate();
        request.setCompanyId(42L);
        request.setPositionName("Operator");
        request.setShowContacts(null);
        when(companyClient.getOwnedCompanyIds(7L)).thenReturn(List.of(42L));
        when(repository.save(any(Vacancy.class))).thenAnswer(invocation -> {
            Vacancy vacancy = invocation.getArgument(0);
            assertNull(vacancy.getId());
            assertEquals(42L, vacancy.getCompanyId());
            assertEquals("Operator", vacancy.getPositionName());
            assertEquals(VacancyStatus.DRAFT, vacancy.getVacancyStatus());
            assertNull(vacancy.getPublishedAt());
            assertEquals(0L, vacancy.getViewsCountCache());
            assertFalse(vacancy.getShowContacts());
            vacancy.setId(100L);
            return vacancy;
        });
        try (MockedStatic<SpringSecurityUtil> security = mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(7L);
            VacancyDTO result = service.createVacancy(request, AppLanguage.UZ);
            assertEquals(42L, result.getCompanyId());
            assertEquals(VacancyStatus.DRAFT, result.getVacancyStatus());
            verify(repository).save(any(Vacancy.class));
        }
    }

    @Test
    void rejectsAnotherSellersCompanyWithoutSaving() {
        VacancyCreate request = new VacancyCreate();
        request.setCompanyId(99L);
        when(companyClient.getOwnedCompanyIds(7L)).thenReturn(List.of(42L));
        try (MockedStatic<SpringSecurityUtil> security = mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(7L);
            assertThrows(AccessDeniedException.class, () -> service.createVacancy(request, AppLanguage.UZ));
            verifyNoInteractions(repository);
        }
    }

    @Test
    void rejectsMissingAuthenticatedProfileWithoutCallingCompanyService() {
        try (MockedStatic<SpringSecurityUtil> security = mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(null);
            assertThrows(AccessDeniedException.class, () -> service.createVacancy(new VacancyCreate(), AppLanguage.UZ));
            verifyNoInteractions(repository, companyClient);
        }
    }
}*/
