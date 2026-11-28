package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.dto.vacancy.*;
import com.example.entity.Vacancy;
import com.example.enums.*;
import com.example.mapper.VacancyDocumentMapper;
import com.example.repository.VacancyRepository;
import com.example.service.*;
import com.example.utils.SpringSecurityUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VacancyJobTypeTest {
    VacancyRepository repository = mock(VacancyRepository.class);
    CompanyClient companies = mock(CompanyClient.class);
    VacancySearchService search = mock(VacancySearchService.class);
    JobCategoryService categories = mock(JobCategoryService.class);
    ResourceBundleService messages = mock(ResourceBundleService.class);
    VacancyServiceImpl seller = new VacancyServiceImpl(repository, companies, search,
            new ModelMapper(), messages, categories);
    BuyerVacancyServiceImpl buyer = new BuyerVacancyServiceImpl(repository, new ModelMapper(),
            messages, categories, search);

    @BeforeEach void setup() {
        when(repository.save(any(Vacancy.class))).thenAnswer(call -> {
            Vacancy vacancy = call.getArgument(0);
            if (vacancy.getId() == null) vacancy.setId(42L);
            return vacancy;
        });
        when(companies.getOwnedCompanyIds(7L)).thenReturn(List.of(3L));
    }

    @Test void sellerAndBuyerCreatePersistAndReturnBothTypes() {
        try (var auth = mockStatic(SpringSecurityUtil.class)) {
            auth.when(SpringSecurityUtil::getProfileId).thenReturn(7L);
            for (JobType type : JobType.values()) {
                var request = new VacancyCreate();
                request.setCompanyId(3L);
                request.setJobType(type);
                assertEquals(type, seller.createVacancy(request, AppLanguage.UZ).getJobType());
                verify(search).index(argThat(doc -> doc.getJobType() == type));
                var buyerRequest = new VacancyCreateBuyer();
                buyerRequest.setJobType(type);
                assertEquals(type, seller.createVacancyBuyer(buyerRequest, AppLanguage.UZ).getJobType());
            }
        }
    }

    @Test void sellerCanClassifyLegacyVacanciesAndOlderClientsPreserveSelections() {
        var vacancy = vacancy();
        vacancy.setCompanyId(3L);
        when(repository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(vacancy));
        try (var auth = mockStatic(SpringSecurityUtil.class)) {
            auth.when(SpringSecurityUtil::getProfileId).thenReturn(7L);
            assertNull(seller.updateVacancy(new VacancyRequest(), 42L, AppLanguage.UZ).getJobType());
            for (JobType type : JobType.values()) {
                var request = new VacancyRequest();
                request.setJobType(type);
                assertEquals(type, seller.updateVacancy(request, 42L, AppLanguage.UZ).getJobType());
                verify(search).update(argThat(doc -> doc.getJobType() == type));
                assertEquals(type, seller.updateVacancy(new VacancyRequest(), 42L, AppLanguage.UZ).getJobType());
                assertEquals(3L, vacancy.getCompanyId());
                assertEquals(42L, vacancy.getId());
            }
        }
    }

    @Test void buyerCanClassifyLegacyVacanciesAndOlderClientsPreserveSelections() {
        var vacancy = vacancy();
        vacancy.setBuyerId(7L);
        when(repository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(vacancy));
        try (var auth = mockStatic(SpringSecurityUtil.class)) {
            auth.when(SpringSecurityUtil::getProfileId).thenReturn(7L);
            assertNull(buyer.update(42L, new VacancyRequest(), AppLanguage.UZ).getJobType());
            for (JobType type : JobType.values()) {
                var request = new VacancyRequest();
                request.setJobType(type);
                assertEquals(type, buyer.update(42L, request, AppLanguage.UZ).getJobType());
                assertEquals(type, buyer.update(42L, new VacancyRequest(), AppLanguage.UZ).getJobType());
                assertEquals(7L, vacancy.getBuyerId());
            }
        }
    }

    @Test void publicDtoAndSearchDocumentsRetainTypesAndLegacyNulls() {
        var vacancy = vacancy();
        for (JobType type : new JobType[]{null, JobType.PERMANENT, JobType.TEMPORARY}) {
            vacancy.setJobType(type);
            var document = VacancyDocumentMapper.from(vacancy);
            assertEquals(type, document.getJobType());
            assertEquals(type, seller.toPublicDTO(document).getJobType());
            assertEquals(type, new ModelMapper().map(vacancy, PublicVacancyDTO.class).getJobType());
        }
    }

    private Vacancy vacancy() {
        var vacancy = new Vacancy();
        vacancy.setId(42L);
        return vacancy;
    }
}
