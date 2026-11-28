package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.dto.vacancy.*;
import com.example.document.VacancyDocument;
import com.example.entity.Vacancy;
import com.example.enums.*;
import com.example.repository.VacancyRepository;
import com.example.service.ResourceBundleService;
import com.example.service.VacancySearchService;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class VacancyPublicationRegressionTest {
    VacancyRepository repository = mock(VacancyRepository.class);
    CompanyClient companies = mock(CompanyClient.class);
    VacancySearchService search = mock(VacancySearchService.class);
    VacancyServiceImpl service = new VacancyServiceImpl(repository, companies, search,
            new ModelMapper(), mock(ResourceBundleService.class), mock(com.example.service.JobCategoryService.class));

    @Test void moderationPublishesDocumentWithDateAndSameDatabaseId() {
        Vacancy vacancy = new Vacancy();
        vacancy.setId(42L);
        vacancy.setVacancyStatus(VacancyStatus.UNDER_MODERATION);
        when(repository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(vacancy));
        when(repository.save(vacancy)).thenReturn(vacancy);
        VacancyModerationComment comment = new VacancyModerationComment();
        comment.setComment("Approved");
        service.vacancyModeration(42L, VacancyModeration.PUBLISHED, comment, AppLanguage.UZ);
        verify(search).update(argThat(doc -> doc.getId().equals("42")
                && doc.getVacancyStatus() == VacancyStatus.PUBLISHED
                && doc.getPublishedAt() != null && Boolean.FALSE.equals(doc.getDeleted())));
    }

    @Test void olderPublishedVacanciesAreReturnedWhenSearchIndexHasNoMatches() {
        VacancyFilter filter = new VacancyFilter();
        Vacancy vacancy = new Vacancy();
        vacancy.setId(42L);
        vacancy.setPositionName("Published operator");
        vacancy.setVacancyStatus(VacancyStatus.PUBLISHED);
        when(search.search(eq(filter), any(Pageable.class)))
                .thenAnswer(call -> Page.empty(call.getArgument(1)));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(vacancy), call.getArgument(1), 1));
        var result = service.getVacancies(filter, AppLanguage.UZ);
        assertEquals(1, result.getTotalElements());
        assertEquals(42L, result.getContent().get(0).getId());
        assertEquals("Published operator", result.getContent().get(0).getPositionName());
        assertNull(result.getContent().get(0).getContacts());
    }

    @Test void indexedResultsKeepSearchPaginationWithoutDatabaseFallback() {
        VacancyFilter filter = new VacancyFilter();
        when(search.search(eq(filter), any(Pageable.class)))
                .thenAnswer(call -> new PageImpl<>(List.of(VacancyDocument.builder().id("42").build()), call.getArgument(1), 1));
        assertEquals(42L, service.getVacancies(filter, AppLanguage.UZ).getContent().get(0).getId());
        verifyNoInteractions(repository);
    }
}