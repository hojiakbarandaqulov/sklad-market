package com.example.service.impl;

import com.example.dto.resume.ResumeRequest;
import com.example.entity.Resume;
import com.example.enums.AppLanguage;
import com.example.exp.AppBadException;
import com.example.exp.AppNotFoundException;
import com.example.repository.ResumeRepository;
import com.example.service.ResourceBundleService;
import com.example.utils.SpringSecurityUtil;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ResumeServiceImplTest {
    private final ResumeRepository repository = mock(ResumeRepository.class);
    private final ResourceBundleService messages = mock(ResourceBundleService.class);
    private final com.example.repository.JobApplicationRepository applications = mock(com.example.repository.JobApplicationRepository.class);
    private final com.example.config.clent.CompanyClient companies = mock(com.example.config.clent.CompanyClient.class);
    private final ResumeServiceImpl service = new ResumeServiceImpl(repository, new ModelMapper(), messages, applications, companies, mock(com.example.config.clent.FileClient.class));
    private MockedStatic<SpringSecurityUtil> security;

    @BeforeEach void setup() {
        security = mockStatic(SpringSecurityUtil.class);
        security.when(SpringSecurityUtil::getProfileId).thenReturn(25L);
        when(messages.getMessage(anyString(), any(AppLanguage.class))).thenAnswer(i -> i.getArgument(0));
    }
    @AfterEach void cleanup() {
        security.close();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private ResumeRequest request() {
        ResumeRequest r = new ResumeRequest();
        r.setTitle(" Java developer "); r.setFullName("Ali Valiyev");
        r.setPhone("+998901234567"); r.setEmail("ali@example.com"); r.setRegionId(1L);
        return r;
    }
    private Resume owned() {
        Resume r = new Resume(); r.setId(501L); r.setCandidateId(25L); r.setTitle("Old");
        when(repository.findByIdAndCandidateIdAndDeletedFalse(501L, 25L)).thenReturn(Optional.of(r));
        return r;
    }
    @Test void createUsesJwtOwnerAndReturnsSavedId() {
        when(repository.save(any(Resume.class))).thenAnswer(i -> {
            Resume r = i.getArgument(0);
            assertNull(r.getId()); assertEquals(25L, r.getCandidateId()); assertFalse(r.getDeleted());
            r.setId(501L); return r;
        });
        var dto = service.createResume(request(), AppLanguage.UZ);
        assertEquals(501L, dto.getId()); assertEquals("Java developer", dto.getTitle());
    }
    @Test void updatePreservesIdentityAndClearsOmittedOptionalFields() {
        Resume r = owned(); r.setSkills("Old skills");
        when(repository.save(r)).thenReturn(r);
        var dto = service.updateResume(501L, request(), AppLanguage.UZ);
        verify(repository).save(same(r));
        assertEquals(501L, dto.getId()); assertEquals(25L, dto.getCandidateId()); assertNull(r.getSkills());
    }
    @Test void missingForeignOrDeletedResumeCannotBeReadUpdatedOrDeleted() {
        when(repository.findByIdAndCandidateIdAndDeletedFalse(999L,25L)).thenReturn(Optional.empty());
        assertThrows(AppNotFoundException.class, () -> service.getResume(999L, AppLanguage.UZ));
        assertThrows(AppNotFoundException.class, () -> service.updateResume(999L, request(), AppLanguage.UZ));
        assertThrows(AppNotFoundException.class, () -> service.deleteResume(999L, AppLanguage.UZ));
        verify(repository, never()).save(any());
    }
    @Test void deleteOnlySetsSoftDeleteFlag() {
        Resume r = owned();
        service.deleteResume(501L, AppLanguage.UZ);
        assertTrue(r.getDeleted()); verify(repository).save(same(r)); verify(repository, never()).delete(any());
    }
    @Test void listFiltersByOwnerAndPreservesPagination() {
        Resume r = new Resume(); r.setId(501L); r.setCandidateId(25L);
        when(repository.findAllByCandidateIdAndDeletedFalse(eq(25L), any(Pageable.class)))
            .thenAnswer(i -> new PageImpl<>(List.of(r), i.getArgument(1), 21));
        var result = service.getMyResumes(2,20,AppLanguage.UZ);
        assertEquals(1,result.getNumber()); assertEquals(21,result.getTotalElements());
        assertEquals(501L,result.getContent().get(0).getId());
        verify(repository).findAllByCandidateIdAndDeletedFalse(eq(25L), argThat(p -> p.getOffset()==20 && p.getSort().getOrderFor("id").isDescending()));
    }
    @Test void invalidPaginationDoesNotQueryDatabase() {
        for (int[] p : new int[][]{{0,20},{1,0},{1,101}}) {
            assertThrows(AppBadException.class, () -> service.getMyResumes(p[0],p[1],AppLanguage.UZ));
        }
        verifyNoInteractions(repository);
    }
    @Test void missingProfileDoesNotQueryDatabase() {
        security.when(SpringSecurityUtil::getProfileId).thenReturn(null);
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.createResume(request(),AppLanguage.UZ));
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> service.getMyResumes(1,20,AppLanguage.UZ));
        verifyNoInteractions(repository);
    }
    private void authenticateRole(String role) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "test", "", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role))));
    }
    private Resume candidateResume() {
        Resume resume = new Resume();
        resume.setId(501L); resume.setCandidateId(26L); resume.setTitle("Candidate resume");
        when(repository.findByIdAndDeletedFalse(501L)).thenReturn(Optional.of(resume));
        return resume;
    }
    @Test void companyOwnerReadsApplicationResumeWithoutServiceRoleCheck() {
        authenticateRole("SELLER");
        candidateResume();
        when(companies.getOwnedCompanyIds(25L)).thenReturn(List.of(7L));
        when(applications.existsResumeApplicationForCompanies(501L, 26L, List.of(7L))).thenReturn(true);
        var result = service.getResume(501L, AppLanguage.UZ);
        assertEquals(501L, result.getId());
        assertEquals(26L, result.getCandidateId());
        assertEquals("Candidate resume", result.getTitle());
        verify(applications).existsResumeApplicationForCompanies(501L, 26L, List.of(7L));
    }
    @Test void sellerCannotReadAnUnrelatedResume() {
        authenticateRole("SELLER");
        candidateResume();
        when(companies.getOwnedCompanyIds(25L)).thenReturn(List.of(8L));
        assertThrows(AppNotFoundException.class, () -> service.getResume(501L, AppLanguage.UZ));
        verify(repository, never()).save(any());
    }
    @Test void sellerWithoutCompanyCannotReadCandidatesResume() {
        authenticateRole("SELLER");
        candidateResume();
        when(companies.getOwnedCompanyIds(25L)).thenReturn(List.of());
        assertThrows(AppNotFoundException.class, () -> service.getResume(501L, AppLanguage.UZ));
        verifyNoInteractions(applications);
    }
    @Test void buyerCannotReadOtherCandidatesResume() {
        authenticateRole("BUYER");
        assertThrows(AppNotFoundException.class, () -> service.getResume(501L, AppLanguage.UZ));
        verifyNoInteractions(companies, applications);
        verify(repository).findByIdAndDeletedFalse(501L);
    }
    @Test void sellerReadPermissionDoesNotAllowChangingCandidatesResume() {
        authenticateRole("SELLER");
        assertThrows(AppNotFoundException.class, () -> service.updateResume(501L, request(), AppLanguage.UZ));
        assertThrows(AppNotFoundException.class, () -> service.deleteResume(501L, AppLanguage.UZ));
        verifyNoInteractions(companies, applications);
        verify(repository, never()).save(any());
    }
    @Test void ownerReadsOwnResumeWithoutCompanyRequest() {
        authenticateRole("BUYER");
        owned();
        assertEquals(501L, service.getResume(501L, AppLanguage.UZ).getId());
        verifyNoInteractions(companies, applications);
    }
}