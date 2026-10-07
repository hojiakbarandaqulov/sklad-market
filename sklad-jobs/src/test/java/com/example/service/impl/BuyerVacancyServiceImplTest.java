package com.example.service.impl;

import com.example.dto.vacancy.VacancyRequest;
import com.example.entity.Vacancy;
import com.example.enums.AppLanguage;
import com.example.enums.VacancyStatus;
import com.example.repository.VacancyRepository;
import com.example.service.ResourceBundleService;
import com.example.utils.SpringSecurityUtil;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BuyerVacancyServiceImplTest {
    VacancyRepository repository = mock(VacancyRepository.class);
    ResourceBundleService messages = mock(ResourceBundleService.class);
    BuyerVacancyServiceImpl service = new BuyerVacancyServiceImpl(repository, new ModelMapper(), messages);
    MockedStatic<SpringSecurityUtil> auth;
    Vacancy vacancy;

    @BeforeEach void setup() {
        auth = mockStatic(SpringSecurityUtil.class);
        auth.when(SpringSecurityUtil::getProfileId).thenReturn(25L);
        vacancy = new Vacancy();
        vacancy.setId(10L);
        vacancy.setBuyerId(25L);
        vacancy.setVacancyStatus(VacancyStatus.PUBLISHED);
        when(repository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(vacancy));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }
    @AfterEach void cleanup() { auth.close(); }

    @Test void updatePreservesOwnerAndReturnsToDraft() {
        vacancy.setPublishedAt(Instant.now());
        var request = new VacancyRequest();
        request.setPositionName("Updated position");
        service.update(10L, request, AppLanguage.UZ);
        assertEquals(25L, vacancy.getBuyerId());
        assertNull(vacancy.getCompanyId());
        assertEquals("Updated position", vacancy.getPositionName());
        assertEquals(VacancyStatus.DRAFT, vacancy.getVacancyStatus());
        assertNull(vacancy.getPublishedAt());
        verify(repository).save(vacancy);
    }

    @Test void closeArchiveAndSubmitPersistStatuses() {
        service.close(10L, AppLanguage.UZ);
        assertEquals(VacancyStatus.CLOSED, vacancy.getVacancyStatus());
        service.archive(10L, AppLanguage.UZ);
        assertEquals(VacancyStatus.ARCHIVE, vacancy.getVacancyStatus());
        service.submit(10L, AppLanguage.UZ);
        assertEquals(VacancyStatus.UNDER_MODERATION, vacancy.getVacancyStatus());
        verify(repository, times(3)).save(vacancy);
    }

    @Test void foreignBuyerCannotMutateVacancy() {
        vacancy.setBuyerId(26L);
        assertThrows(AccessDeniedException.class, () -> service.update(10L, new VacancyRequest(), AppLanguage.UZ));
        assertThrows(AccessDeniedException.class, () -> service.close(10L, AppLanguage.UZ));
        assertThrows(AccessDeniedException.class, () -> service.archive(10L, AppLanguage.UZ));
        assertThrows(AccessDeniedException.class, () -> service.submit(10L, AppLanguage.UZ));
        verify(repository, never()).save(any());
        assertEquals(VacancyStatus.PUBLISHED, vacancy.getVacancyStatus());
    }

    @Test void companyVacancyCannotBeChangedThroughBuyerApi() {
        vacancy.setCompanyId(7L);
        assertThrows(AccessDeniedException.class, () -> service.close(10L, AppLanguage.UZ));
        verify(repository, never()).save(any());
    }
}