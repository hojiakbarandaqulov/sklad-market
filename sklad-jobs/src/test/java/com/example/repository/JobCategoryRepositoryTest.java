package com.example.repository;

import com.example.dto.category.JobCategoryRequest;
import com.example.dto.vacancy.VacancyFilter;
import com.example.entity.Vacancy;
import com.example.enums.AppLanguage;
import com.example.enums.VacancyStatus;
import com.example.exp.AppBadException;
import com.example.exp.AppNotFoundException;
import com.example.repository.specification.VacancySpecifications;
import com.example.service.ResourceBundleService;
import com.example.service.impl.JobCategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=update"
})
@ContextConfiguration(classes = JobCategoryRepositoryTest.Config.class)
class JobCategoryRepositoryTest {
    @Configuration @EnableAutoConfiguration @EntityScan("com.example.entity")
    @EnableJpaRepositories("com.example.repository") static class Config {}
    @Autowired JobCategoryRepository categories;
    @Autowired VacancyRepository vacancies;
    JobCategoryServiceImpl service;

    @BeforeEach void setup() {
        var messages = mock(ResourceBundleService.class);
        when(messages.getMessage(anyString(), any(AppLanguage.class))).thenAnswer(i -> i.getArgument(0));
        service = new JobCategoryServiceImpl(categories, messages);
    }

    private JobCategoryRequest request(boolean active) {
        var request = new JobCategoryRequest();
        request.setNameUz(" IT va dasturlash ");
        request.setNameRu("ИТ и программирование");
        request.setNameEn("IT and programming");
        request.setIsActive(active);
        return request;
    }

    @Test void crudReturnsRequestedTranslationAndHidesInactiveAndDeletedCategories() {
        var created = service.create(request(true), AppLanguage.UZ);
        assertEquals("IT va dasturlash", created.name());
        assertEquals("ИТ и программирование", service.get(created.id(), true, AppLanguage.RU).name());
        var updated = service.update(created.id(), request(false), AppLanguage.EN);
        assertEquals("IT and programming", updated.name());
        assertEquals(0, service.list(1, 20, true, AppLanguage.UZ).getTotalElements());
        assertEquals(1, service.list(1, 20, false, AppLanguage.UZ).getTotalElements());
        assertThrows(AppBadException.class, () -> service.validateSelection(created.id(), AppLanguage.UZ));
        assertThrows(AppNotFoundException.class, () -> service.get(created.id(), true, AppLanguage.UZ));
        service.update(created.id(), request(true), AppLanguage.UZ);
        service.validateSelection(created.id(), AppLanguage.UZ);
        service.delete(created.id(), AppLanguage.UZ);
        assertEquals(0, service.list(1, 20, false, AppLanguage.UZ).getTotalElements());
        assertThrows(AppNotFoundException.class, () -> service.get(created.id(), false, AppLanguage.UZ));
        assertThrows(AppBadException.class, () -> service.validateSelection(created.id(), AppLanguage.UZ));
        assertTrue(categories.findById(created.id()).orElseThrow().getDeleted());
    }

    @Test void deletionPreservesVacancyReferenceAndFilterCombinesCategoryWithPublicationAndPagination() {
        long category = service.create(request(true), AppLanguage.UZ).id();
        long other = service.create(request(true), AppLanguage.UZ).id();
        Vacancy first = save(category, VacancyStatus.PUBLISHED);
        save(category, VacancyStatus.PUBLISHED);
        save(other, VacancyStatus.PUBLISHED);
        save(null, VacancyStatus.PUBLISHED);
        save(category, VacancyStatus.DRAFT);
        Vacancy deleted = save(category, VacancyStatus.PUBLISHED);
        deleted.setDeleted(true);
        vacancies.saveAndFlush(deleted);
        var filter = new VacancyFilter();
        assertEquals(4, vacancies.findAll(VacancySpecifications.published(filter)).size());
        filter.setCategoryId(category);
        var result = vacancies.findAll(VacancySpecifications.published(filter), PageRequest.of(0, 1));
        assertEquals(2, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        service.delete(category, AppLanguage.UZ);
        categories.flush();
        assertEquals(category, vacancies.findById(first.getId()).orElseThrow().getCategoryId());
        assertEquals(2, vacancies.findAll(VacancySpecifications.published(filter)).size());
    }

    @Test void invalidIdsAreRejectedAndEmptyListsAreSuccessful() {
        service.validateSelection(null, AppLanguage.UZ);
        for (Long id : new Long[]{0L, -1L, 999L}) {
            assertThrows(AppBadException.class, () -> service.validateSelection(id, AppLanguage.UZ));
        }
        assertEquals(0, service.list(1, 20, true, AppLanguage.UZ).getTotalElements());
        assertThrows(AppBadException.class, () -> service.list(0, 20, true, AppLanguage.UZ));
    }

    private Vacancy save(Long categoryId, VacancyStatus status) {
        var vacancy = new Vacancy();
        vacancy.setCategoryId(categoryId);
        vacancy.setVacancyStatus(status);
        return vacancies.saveAndFlush(vacancy);
    }
}
