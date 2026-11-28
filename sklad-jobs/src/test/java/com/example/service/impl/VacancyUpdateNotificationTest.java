package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.dto.vacancy.VacancyRequest;
import com.example.entity.Vacancy;
import com.example.enums.*;
import com.example.repository.VacancyRepository;
import com.example.service.*;
import com.example.utils.SpringSecurityUtil;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class VacancyUpdateNotificationTest {
    VacancyRepository repository = mock(VacancyRepository.class);
    CompanyClient companies = mock(CompanyClient.class);
    VacancySearchService search = mock(VacancySearchService.class);
    ResourceBundleService messages = mock(ResourceBundleService.class);
    JobCategoryService categories = mock(JobCategoryService.class);
    VacancyServiceImpl seller = new VacancyServiceImpl(repository, companies, search, new ModelMapper(), messages, categories);
    BuyerVacancyServiceImpl buyer = new BuyerVacancyServiceImpl(repository, new ModelMapper(), messages, categories, search);

    Vacancy prepare(boolean companyOwned, boolean failSave) {
        Vacancy vacancy = new Vacancy(); vacancy.setId(77L); vacancy.setVacancyStatus(VacancyStatus.PUBLISHED);
        if (companyOwned) vacancy.setCompanyId(3L); else vacancy.setBuyerId(901L);
        when(repository.findByIdAndDeletedFalse(77L)).thenReturn(Optional.of(vacancy));
        when(companies.getOwnedCompanyIds(901L)).thenReturn(List.of(3L));
        if (failSave) when(repository.save(vacancy)).thenThrow(new IllegalStateException("Database unavailable"));
        else when(repository.save(vacancy)).thenReturn(vacancy);
        return vacancy;
    }
    void update(boolean companyOwned, long viewerId) {
        VacancyRequest request = new VacancyRequest(); request.setPositionName("Updated operator");
        try (var auth = mockStatic(SpringSecurityUtil.class)) {
            auth.when(SpringSecurityUtil::getProfileId).thenReturn(viewerId);
            if (companyOwned) seller.updateVacancy(request, 77L, AppLanguage.UZ);
            else buyer.update(77L, request, AppLanguage.UZ);
        }
    }
    @Test void sellerUpdateStillEntersModerationWithoutKafkaDependency() {
        Vacancy saved = prepare(true, false); update(true, 901L);
        assertEquals(VacancyStatus.UNDER_MODERATION, saved.getVacancyStatus());
        assertEquals("Updated operator", saved.getPositionName());
        verify(repository).save(saved);
        verify(search).update(argThat(doc -> doc.getId().equals("77") && doc.getVacancyStatus() == VacancyStatus.UNDER_MODERATION));
    }
    @Test void buyerUpdateStillEntersModerationWithoutKafkaDependency() {
        Vacancy saved = prepare(false, false); update(false, 901L);
        assertEquals(VacancyStatus.UNDER_MODERATION, saved.getVacancyStatus());
        assertEquals("Updated operator", saved.getPositionName());
        verify(repository).save(saved);
        verify(search).update(argThat(doc -> doc.getId().equals("77") && doc.getVacancyStatus() == VacancyStatus.UNDER_MODERATION));
    }
    @Test void failedSellerUpdateStillPropagatesFailure() {
        prepare(true, true); assertThrows(IllegalStateException.class, () -> update(true, 901L));
        verifyNoInteractions(search);
    }
    @Test void failedBuyerUpdateStillPropagatesFailure() {
        prepare(false, true); assertThrows(IllegalStateException.class, () -> update(false, 901L));
        verifyNoInteractions(search);
    }
    @Test void anotherBuyerCannotUpdateVacancy() {
        prepare(false, false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> update(false, 902L));
        verifyNoInteractions(search);
    }
}