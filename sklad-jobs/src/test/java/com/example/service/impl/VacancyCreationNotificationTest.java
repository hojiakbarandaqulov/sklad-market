package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.dto.vacancy.*;
import com.example.entity.Vacancy;
import com.example.enums.AppLanguage;
import com.example.repository.VacancyRepository;
import com.example.service.*;
import com.example.utils.SpringSecurityUtil;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class VacancyCreationNotificationTest {
    VacancyRepository repository = mock(VacancyRepository.class);
    CompanyClient companies = mock(CompanyClient.class);
    VacancySearchService search = mock(VacancySearchService.class);
    // Creation no longer requires a Kafka producer or notification service.
    VacancyServiceImpl service = new VacancyServiceImpl(repository, companies, search, new ModelMapper(),
            mock(ResourceBundleService.class), mock(JobCategoryService.class));

    void saveWithId() {
        when(repository.save(any(Vacancy.class))).thenAnswer(call -> {
            Vacancy saved = call.getArgument(0); saved.setId(77L); return saved;
        });
    }
    @Test void sellerCreationPersistsAndIndexesWithoutKafkaDependency() {
        saveWithId(); when(companies.getOwnedCompanyIds(901L)).thenReturn(List.of(3L));
        VacancyCreate request = new VacancyCreate(); request.setCompanyId(3L); request.setPositionName("Operator");
        try (var security = mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(901L);
            assertEquals("Operator", service.createVacancy(request, AppLanguage.UZ).getPositionName());
        }
        verify(repository).save(argThat(saved -> saved.getPositionName().equals("Operator")));
        verify(search).index(argThat(doc -> doc.getId().equals("77")));
    }
    @Test void buyerCreationPersistsWithoutKafkaDependency() {
        saveWithId();
        VacancyCreateBuyer request = new VacancyCreateBuyer(); request.setPositionName("Developer");
        try (var security = mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(901L);
            assertEquals("Developer", service.createVacancyBuyer(request, AppLanguage.UZ).getPositionName());
        }
        verify(repository).save(argThat(saved -> saved.getId().equals(77L) && saved.getBuyerId().equals(901L)));
    }
    @Test void failedCreationStillPropagatesDatabaseFailure() {
        when(repository.save(any(Vacancy.class))).thenThrow(new IllegalStateException("Database unavailable"));
        try (var security = mockStatic(SpringSecurityUtil.class)) {
            security.when(SpringSecurityUtil::getProfileId).thenReturn(901L);
            assertThrows(IllegalStateException.class, () -> service.createVacancyBuyer(new VacancyCreateBuyer(), AppLanguage.UZ));
        }
        verifyNoInteractions(search);
    }
}