package com.example.service.impl;

import com.example.config.clent.CompanyClient;
import com.example.entity.*;
import com.example.enums.AppLanguage;
import com.example.exp.AppNotFoundException;
import com.example.repository.*;
import com.example.service.ResourceBundleService;
import com.example.utils.SpringSecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationResumeAccessTest {
    private final JobApplicationRepository applications = mock(JobApplicationRepository.class);
    private final ResumeRepository resumes = mock(ResumeRepository.class);
    private final CompanyClient companies = mock(CompanyClient.class);
    private final ResourceBundleService messages = mock(ResourceBundleService.class);
    private final JobApplicationServiceImpl service = new JobApplicationServiceImpl(
            applications, resumes, mock(VacancyRepository.class), messages,
            new ModelMapper(), new ObjectMapper().findAndRegisterModules(), companies);
    private MockedStatic<SpringSecurityUtil> security;
    private JobApplication application;

    @BeforeEach
    void setUp() {
        security = mockStatic(SpringSecurityUtil.class);
        security.when(SpringSecurityUtil::getProfileId).thenReturn(77L);
        Vacancy vacancy = new Vacancy();
        vacancy.setId(42L);
        vacancy.setCompanyId(7L);
        application = new JobApplication();
        application.setId(9001L);
        application.setCandidateId(25L);
        application.setVacancy(vacancy);
        application.setResumeId(501L);
        application.setResumeSnapshot("""
                {"id":501,"candidateId":25,"title":"Submitted resume","skills":"Java"}
                """);
        when(applications.findByIdAndDeletedFalse(9001L)).thenReturn(Optional.of(application));
        when(messages.getMessage("resume.not.found", AppLanguage.UZ)).thenReturn("Resume not found");
        when(messages.getMessage("application.not.found", AppLanguage.UZ)).thenReturn("Application not found");
    }

    @AfterEach
    void tearDown() { security.close(); }

    @Test
    void employerReadsSubmittedSnapshotWithoutAccessingCandidatesPrivateResumeEndpoint() {
        when(companies.getOwnedCompanyIds(77L)).thenReturn(List.of(7L));
        var result = service.getApplicationResume(9001L, AppLanguage.UZ);
        assertEquals(501L, result.getId());
        assertEquals("Submitted resume", result.getTitle());
        assertEquals("Java", result.getSkills());
        verifyNoInteractions(resumes);
    }

    @Test
    void candidateReadsOwnSubmittedSnapshot() {
        security.when(SpringSecurityUtil::getProfileId).thenReturn(25L);
        assertEquals("Submitted resume", service.getApplicationResume(9001L, AppLanguage.UZ).getTitle());
        verifyNoInteractions(companies, resumes);
    }

    @Test
    void anotherCompanyCannotReadTheResume() {
        when(companies.getOwnedCompanyIds(77L)).thenReturn(List.of(8L));
        assertThrows(AccessDeniedException.class, () -> service.getApplicationResume(9001L, AppLanguage.UZ));
        verifyNoInteractions(resumes);
    }

    @Test
    void oldApplicationWithoutSnapshotReadsResumeUsingCandidateId() {
        application.setResumeSnapshot(null);
        when(companies.getOwnedCompanyIds(77L)).thenReturn(List.of(7L));
        Resume resume = new Resume();
        resume.setId(501L);
        resume.setCandidateId(25L);
        resume.setTitle("Legacy resume");
        when(resumes.findByIdAndCandidateIdAndDeletedFalse(501L, 25L)).thenReturn(Optional.of(resume));
        assertEquals("Legacy resume", service.getApplicationResume(9001L, AppLanguage.UZ).getTitle());
        verify(resumes).findByIdAndCandidateIdAndDeletedFalse(501L, 25L);
        verify(resumes, never()).findByIdAndCandidateIdAndDeletedFalse(501L, 77L);
    }

    @Test
    void oldApplicationWithDeletedOrForeignResumeReturnsNotFound() {
        application.setResumeSnapshot(null);
        when(companies.getOwnedCompanyIds(77L)).thenReturn(List.of(7L));
        assertThrows(AppNotFoundException.class, () -> service.getApplicationResume(9001L, AppLanguage.UZ));
    }

    @Test
    void missingApplicationDoesNotExposeAResume() {
        assertThrows(AppNotFoundException.class, () -> service.getApplicationResume(999L, AppLanguage.UZ));
        verifyNoInteractions(companies, resumes);
    }

    @Test
    void missingProfileDoesNotQueryPrivateData() {
        security.when(SpringSecurityUtil::getProfileId).thenReturn(null);
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.getApplicationResume(9001L, AppLanguage.UZ));
        verify(applications, never()).findByIdAndDeletedFalse(any());
        verifyNoInteractions(companies, resumes);
    }
}
