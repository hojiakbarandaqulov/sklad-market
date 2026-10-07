package com.example.repository;

import com.example.entity.Vacancy;
import com.example.enums.*;
import com.example.exp.AppBadException;
import com.example.service.ResourceBundleService;
import com.example.service.impl.VacancyFavoriteServiceImpl;
import com.example.utils.SpringSecurityUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop"})
@ContextConfiguration(classes = PublicVacancyRepositoryTest.Config.class)
class VacancyFavoriteTest {
    @Autowired VacancyRepository vacancies;
    @Autowired VacancyFavoriteRepository favorites;
    @Autowired EntityManager em;
    VacancyFavoriteServiceImpl service;
    MockedStatic<SpringSecurityUtil> auth;
    Vacancy vacancy;

    @BeforeEach void setup() {
        var messages = mock(ResourceBundleService.class);
        when(messages.getMessage(anyString(), any())).thenAnswer(i -> i.getArgument(0));
        service = new VacancyFavoriteServiceImpl(favorites, vacancies, new ModelMapper(), messages);
        auth = mockStatic(SpringSecurityUtil.class);
        auth.when(SpringSecurityUtil::getProfileId).thenReturn(25L);
        vacancy = new Vacancy();
        vacancy.setPositionName("Developer");
        vacancy.setVacancyStatus(VacancyStatus.PUBLISHED);
        vacancies.saveAndFlush(vacancy);
    }
    @AfterEach void cleanup() { auth.close(); }

    @Test void addRetryRemoveAndRestoreUseOneFavoriteRow() {
        assertTrue(service.add(vacancy.getId(), AppLanguage.UZ).favorited());
        service.add(vacancy.getId(), AppLanguage.UZ);
        assertEquals(1, favorites.count());
        assertEquals(1, service.getCount(AppLanguage.UZ).count());
        assertFalse(service.remove(vacancy.getId(), AppLanguage.UZ).favorited());
        em.flush(); em.clear();
        assertEquals(0, service.getCount(AppLanguage.UZ).count());
        service.add(vacancy.getId(), AppLanguage.UZ);
        assertEquals(1, favorites.count());
        assertEquals(1, service.getCount(AppLanguage.UZ).count());
    }

    @Test void usersHaveSeparateListsAndCannotRemoveEachOthersFavorites() {
        service.add(vacancy.getId(), AppLanguage.UZ);
        auth.when(SpringSecurityUtil::getProfileId).thenReturn(26L);
        assertTrue(service.getFavorites(1, 20, AppLanguage.UZ).isEmpty());
        assertEquals(0, service.getCount(AppLanguage.UZ).count());
        assertThrows(AppBadException.class, () -> service.remove(vacancy.getId(), AppLanguage.UZ));
        auth.when(SpringSecurityUtil::getProfileId).thenReturn(25L);
        assertEquals(vacancy.getId(), service.getFavorites(1, 20, AppLanguage.UZ).getContent().get(0).getId());
    }

    @Test void closedArchivedDraftAndDeletedVacanciesAreNotListedOrCounted() {
        service.add(vacancy.getId(), AppLanguage.UZ);
        for (var status : List.of(VacancyStatus.CLOSED, VacancyStatus.ARCHIVE, VacancyStatus.DRAFT)) {
            vacancy.setVacancyStatus(status);
            vacancies.saveAndFlush(vacancy);
            assertEquals(0, service.getCount(AppLanguage.UZ).count());
            assertTrue(service.getFavorites(1, 20, AppLanguage.UZ).isEmpty());
            assertThrows(AppBadException.class, () -> service.add(vacancy.getId(), AppLanguage.UZ));
        }
        vacancy.setVacancyStatus(VacancyStatus.PUBLISHED);
        vacancy.setDeleted(true);
        vacancies.saveAndFlush(vacancy);
        assertEquals(0, service.getCount(AppLanguage.UZ).count());
        assertTrue(service.getFavorites(1, 20, AppLanguage.UZ).isEmpty());
        assertThrows(AppBadException.class, () -> service.add(vacancy.getId(), AppLanguage.UZ));
        assertFalse(service.remove(vacancy.getId(), AppLanguage.UZ).favorited());
    }

    @Test void invalidIdsPaginationAndMissingAuthenticationAreRejected() {
        assertThrows(AppBadException.class, () -> service.add(0L, AppLanguage.UZ));
        assertThrows(AppBadException.class, () -> service.getFavorites(0, 20, AppLanguage.UZ));
        assertThrows(AppBadException.class, () -> service.getFavorites(1, 101, AppLanguage.UZ));
        auth.when(SpringSecurityUtil::getProfileId).thenReturn(null);
        assertThrows(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class,
                () -> service.getCount(AppLanguage.UZ));
    }
}