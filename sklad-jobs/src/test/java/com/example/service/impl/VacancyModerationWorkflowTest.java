package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.controller.VacancyController;
import com.example.dto.vacancy.VacancyModerationComment;
import com.example.entity.Vacancy;
import com.example.enums.AppLanguage;
import com.example.enums.VacancyModeration;
import com.example.enums.VacancyStatus;
import com.example.exp.AppBadException;
import com.example.repository.VacancyRepository;
import com.example.service.JobCategoryService;
import com.example.service.ResourceBundleService;
import com.example.service.VacancySearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.modelmapper.ModelMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class VacancyModerationWorkflowTest {
    private final VacancyRepository repository = mock(VacancyRepository.class);
    private final VacancySearchService search = mock(VacancySearchService.class);
    private final ResourceBundleService messages = mock(ResourceBundleService.class);
    private final VacancyServiceImpl service = new VacancyServiceImpl(repository, mock(CompanyClient.class), search,
            new ModelMapper(), messages, mock(JobCategoryService.class));
    private Vacancy vacancy;

    @BeforeEach
    void setUp() {
        vacancy = new Vacancy();
        vacancy.setId(42L);
        vacancy.setVacancyStatus(VacancyStatus.UNDER_MODERATION);
        vacancy.setComment("Previous rejection reason");
        when(repository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(vacancy));
        when(repository.save(vacancy)).thenReturn(vacancy);
        when(messages.getMessage(anyString(), any(AppLanguage.class))).thenAnswer(call -> call.getArgument(0));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "Approval text must not be stored"})
    void approvesWithoutRequiringOrSavingAComment(String comment) {
        var request = new VacancyModerationComment();
        request.setComment(comment);
        service.vacancyModeration(42L, VacancyModeration.PUBLISHED, request, AppLanguage.UZ);
        assertEquals(VacancyStatus.PUBLISHED, vacancy.getVacancyStatus());
        assertNotNull(vacancy.getPublishedAt());
        assertNull(vacancy.getComment());
        verify(search).update(argThat(document -> document.getVacancyStatus() == VacancyStatus.PUBLISHED
                && document.getComment() == null && document.getPublishedAt() != null));
    }

    @Test
    void approvalEndpointAcceptsAnEmptyJsonObjectWithBeanValidationEnabled() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new VacancyController(service)).build();
        mvc.perform(post("/api/v1/vacancy/admin/42/moderation")
                        .param("vacancyModeration", "PUBLISHED")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertEquals(VacancyStatus.PUBLISHED, vacancy.getVacancyStatus());
        assertNull(vacancy.getComment());
        verify(repository).save(vacancy);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void rejectsMissingReasonsWithoutChangingTheVacancy(String comment) {
        var request = new VacancyModerationComment();
        request.setComment(comment);
        var error = assertThrows(AppBadException.class,
                () -> service.vacancyModeration(42L, VacancyModeration.REJECTED, request, AppLanguage.RU));
        assertEquals("vacancy.rejection.reason.required", error.getMessage());
        assertEquals(VacancyStatus.UNDER_MODERATION, vacancy.getVacancyStatus());
        assertEquals("Previous rejection reason", vacancy.getComment());
        verifyNoInteractions(repository, search);
    }

    @Test
    void rejectionStoresItsTrimmedReasonInDatabaseAndSearch() {
        var request = new VacancyModerationComment();
        request.setComment("  Incomplete details  ");
        service.vacancyModeration(42L, VacancyModeration.REJECTED, request, AppLanguage.EN);
        assertEquals(VacancyStatus.REJECTED, vacancy.getVacancyStatus());
        assertEquals("Incomplete details", vacancy.getComment());
        verify(repository).save(vacancy);
        verify(search).update(argThat(document -> document.getVacancyStatus() == VacancyStatus.REJECTED
                && "Incomplete details".equals(document.getComment())));
    }
}
