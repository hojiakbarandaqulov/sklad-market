package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.dto.vacancy.*;
import com.example.entity.Vacancy;
import com.example.enums.*;
import com.example.exp.AppBadException;
import com.example.repository.VacancyRepository;
import com.example.service.*;
import com.example.utils.SpringSecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class VacancyCategoryTest {
    VacancyRepository repository = mock(VacancyRepository.class);
    CompanyClient companies = mock(CompanyClient.class);
    VacancySearchService search = mock(VacancySearchService.class);
    JobCategoryService categories = mock(JobCategoryService.class);
    ResourceBundleService messages = mock(ResourceBundleService.class);
    VacancyServiceImpl service = new VacancyServiceImpl(repository, companies, search,
            new ModelMapper(), messages, categories);
    BuyerVacancyServiceImpl buyerService = new BuyerVacancyServiceImpl(repository, new ModelMapper(),
            messages, categories, search);

    @BeforeEach void setup() {
        when(repository.save(any(Vacancy.class))).thenAnswer(i -> {
            Vacancy vacancy = i.getArgument(0);
            if (vacancy.getId() == null) vacancy.setId(42L);
            return vacancy;
        });
    }

    @Test void sellerAndBuyerCreateReturnSelectedCategoryAndSellerDocumentCarriesIt() {
        when(companies.getOwnedCompanyIds(7L)).thenReturn(List.of(3L));
        try (var auth = mockStatic(SpringSecurityUtil.class)) {
            auth.when(SpringSecurityUtil::getProfileId).thenReturn(7L);
            var seller = new VacancyCreate();
            seller.setCompanyId(3L);
            seller.setCategoryId(10L);
            assertEquals(10L, service.createVacancy(seller, AppLanguage.UZ).getCategoryId());
            verify(search).index(argThat(doc -> Long.valueOf(10L).equals(doc.getCategoryId())));
            var buyer = new VacancyCreateBuyer();
            buyer.setCategoryId(10L);
            assertEquals(10L, service.createVacancyBuyer(buyer, AppLanguage.UZ).getCategoryId());
            verify(categories, times(2)).validateSelection(10L, AppLanguage.UZ);
        }
    }

    @Test void sellerUpdateChangesCategoryAndOmittedCategoryPreservesExistingValue() {
        var vacancy = vacancy();
        vacancy.setCompanyId(3L);
        when(repository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(vacancy));
        when(companies.getOwnedCompanyIds(7L)).thenReturn(List.of(3L));
        var request = new VacancyRequest();
        request.setCategoryId(11L);
        try (var auth = mockStatic(SpringSecurityUtil.class)) {
            auth.when(SpringSecurityUtil::getProfileId).thenReturn(7L);
            assertEquals(11L, service.updateVacancy(request, 42L, AppLanguage.RU).getCategoryId());
            verify(search).update(argThat(doc -> Long.valueOf(11L).equals(doc.getCategoryId())));
            assertEquals(11L, service.updateVacancy(new VacancyRequest(), 42L, AppLanguage.RU).getCategoryId());
        }
    }

    @Test void invalidCategoryDoesNotChangeOrSaveVacancy() {
        var vacancy = vacancy();
        vacancy.setCompanyId(3L);
        when(repository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(vacancy));
        when(companies.getOwnedCompanyIds(7L)).thenReturn(List.of(3L));
        doThrow(new AppBadException("invalid")).when(categories).validateSelection(999L, AppLanguage.UZ);
        var request = new VacancyRequest();
        request.setCategoryId(999L);
        try (var auth = mockStatic(SpringSecurityUtil.class)) {
            auth.when(SpringSecurityUtil::getProfileId).thenReturn(7L);
            assertThrows(AppBadException.class, () -> service.updateVacancy(request, 42L, AppLanguage.UZ));
        }
        assertEquals(10L, vacancy.getCategoryId());
        verify(repository, never()).save(any());
        verifyNoInteractions(search);
    }

    @Test void buyerUpdateIndexesCategoryAfterDatabaseCommit() {
        var vacancy = vacancy();
        vacancy.setBuyerId(7L);
        when(repository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(vacancy));
        var request = new VacancyRequest();
        request.setCategoryId(11L);
        TransactionSynchronizationManager.initSynchronization();
        try (var auth = mockStatic(SpringSecurityUtil.class)) {
            auth.when(SpringSecurityUtil::getProfileId).thenReturn(7L);
            assertEquals(11L, buyerService.update(42L, request, AppLanguage.EN).getCategoryId());
            verifyNoInteractions(search);
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            verify(search).update(argThat(doc -> Long.valueOf(11L).equals(doc.getCategoryId())
                    && doc.getVacancyStatus() == VacancyStatus.DRAFT));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Vacancy vacancy() {
        var vacancy = new Vacancy();
        vacancy.setId(42L);
        vacancy.setCategoryId(10L);
        return vacancy;
    }
}
