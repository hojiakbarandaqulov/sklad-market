package com.example.service.impl;
import com.example.dto.application.*;
import com.example.entity.*;
import com.example.enums.*;
import com.example.exp.*;
import com.example.repository.*;
import com.example.service.ResourceBundleService;
import com.example.utils.SpringSecurityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class JobApplicationServiceImplTest {
    JobApplicationRepository apps=mock(JobApplicationRepository.class);
    ResumeRepository resumes=mock(ResumeRepository.class);
    VacancyRepository vacancies=mock(VacancyRepository.class);
    com.example.config.clent.CompanyClient companies=mock(com.example.config.clent.CompanyClient.class);
    ResourceBundleService messages=mock(ResourceBundleService.class);
    JobApplicationServiceImpl service=new JobApplicationServiceImpl(apps,resumes,vacancies,messages,new ModelMapper(),new ObjectMapper().findAndRegisterModules(),companies);
    MockedStatic<SpringSecurityUtil> security;
    JobApplication application;
    @BeforeEach void setup(){
        security=mockStatic(SpringSecurityUtil.class);security.when(SpringSecurityUtil::getProfileId).thenReturn(25L);
        when(messages.getMessage(anyString(),any(AppLanguage.class))).thenAnswer(i->i.getArgument(0));
        Vacancy v=new Vacancy();v.setId(100L);v.setCompanyId(42L);
        application=new JobApplication();application.setId(9001L);application.setCandidateId(25L);application.setVacancy(v);application.setResumeId(501L);application.setPhone("old-contact");
        when(apps.findOwnedForUpdate(9001L,25L)).thenReturn(Optional.of(application));
        when(apps.save(any(JobApplication.class))).thenAnswer(i->i.getArgument(0));
    }
    @AfterEach void close(){security.close();}
    ApplicationResumeRequest request(){ApplicationResumeRequest r=new ApplicationResumeRequest();r.setResumeId(502L);return r;}
    @Test void withdrawWorksForEveryActiveStatusAndRepeatedCallPreservesTime(){
        for(ApplicationStatus s:List.of(ApplicationStatus.NEW,ApplicationStatus.REVIEWED,ApplicationStatus.IN_COMMUNICATION,ApplicationStatus.INVITED)){
            application.setStatus(s);application.setWithdrawnAt(null);
            assertEquals(ApplicationStatus.WITHDRAWN,service.withdraw(9001L,AppLanguage.UZ).getStatus());
            Instant time=application.getWithdrawnAt();assertNotNull(time);
            service.withdraw(9001L,AppLanguage.UZ);assertEquals(time,application.getWithdrawnAt());
        }
        verify(apps,times(4)).save(application);
    }
    @Test void terminalStatusesRejectMutations(){
        for(ApplicationStatus s:List.of(ApplicationStatus.ACCEPTED,ApplicationStatus.REJECTED,ApplicationStatus.ARCHIVED)){
            application.setStatus(s);
            assertThrows(AppConflictException.class,()->service.withdraw(9001L,AppLanguage.UZ));
            assertThrows(AppConflictException.class,()->service.replaceResume(9001L,request(),AppLanguage.UZ));
        }
        application.setStatus(ApplicationStatus.WITHDRAWN);
        assertThrows(AppConflictException.class,()->service.replaceResume(9001L,request(),AppLanguage.UZ));
        verify(apps,never()).save(any());verifyNoInteractions(resumes);
    }
    @Test void foreignOrDeletedApplicationIsNotAccessible(){
        when(apps.findByIdAndCandidateIdAndDeletedFalse(999L,25L)).thenReturn(Optional.empty());
        when(apps.findOwnedForUpdate(999L,25L)).thenReturn(Optional.empty());
        assertThrows(AppNotFoundException.class,()->service.getMyApplication(999L,AppLanguage.UZ));
        assertThrows(AppNotFoundException.class,()->service.withdraw(999L,AppLanguage.UZ));
        assertThrows(AppNotFoundException.class,()->service.replaceResume(999L,request(),AppLanguage.UZ));
        verify(apps,never()).save(any());verifyNoInteractions(resumes);
    }
    @Test void resumeMustBeOwnedAndNotDeleted(){
        when(resumes.findByIdAndCandidateIdAndDeletedFalse(502L,25L)).thenReturn(Optional.empty());
        assertThrows(AppNotFoundException.class,()->service.replaceResume(9001L,request(),AppLanguage.UZ));
        assertEquals(501L,application.getResumeId());verify(apps,never()).save(any());
    }
    @Test void replacementSnapshotsResumeWithoutChangingContactsOrStatus() throws Exception {
        Resume r=new Resume();r.setId(502L);r.setCandidateId(25L);r.setTitle("Java");r.setPhone("new-contact");
        when(resumes.findByIdAndCandidateIdAndDeletedFalse(502L,25L)).thenReturn(Optional.of(r));
        application.setStatus(ApplicationStatus.REVIEWED);
        var dto=service.replaceResume(9001L,request(),AppLanguage.UZ);
        assertEquals(502L,dto.getResumeId());assertEquals("old-contact",dto.getPhone());assertEquals(ApplicationStatus.REVIEWED,dto.getStatus());
        r.setTitle("Changed later");
        assertEquals("Java",new ObjectMapper().readTree(application.getResumeSnapshot()).get("title").asText());
    }
    @Test void listPassesOwnerStatusAndPagination(){
        when(apps.findMyApplications(eq(25L),eq(ApplicationStatus.NEW),any(Pageable.class)))
            .thenAnswer(i->new PageImpl<>(List.of(application),i.getArgument(2),21));
        var page=service.getMyApplications(ApplicationStatus.NEW,2,20,AppLanguage.UZ);
        assertEquals(21,page.getTotalElements());assertEquals(1,page.getNumber());assertEquals(9001L,page.getContent().get(0).getId());
    }
    @Test void invalidPaginationAndMissingProfileDoNotQuery(){
        assertThrows(AppBadException.class,()->service.getMyApplications(null,0,20,AppLanguage.UZ));
        assertThrows(AppBadException.class,()->service.getMyApplications(null,1,101,AppLanguage.UZ));
        security.when(SpringSecurityUtil::getProfileId).thenReturn(null);
        assertThrows(AuthenticationCredentialsNotFoundException.class,()->service.getMyApplication(9001L,AppLanguage.UZ));
        verify(apps,never()).findMyApplications(any(),any(),any());verify(apps,never()).findByIdAndCandidateIdAndDeletedFalse(any(),any());
    }
    ApplicationCreateRequest createRequest(){
        ApplicationCreateRequest r=new ApplicationCreateRequest();r.setResumeId(501L);r.setConsentAccepted(true);
        r.setFullName("Ali");r.setPhone("+998901234567");r.setEmail("ali@example.com");r.setRegionId(1L);
        return r;
    }
    void allowCreate(){
        application.getVacancy().setVacancyStatus(VacancyStatus.PUBLISHED);
        when(vacancies.findForApplication(100L)).thenReturn(Optional.of(application.getVacancy()));
        Resume resume=new Resume();resume.setId(501L);resume.setCandidateId(25L);resume.setTitle("Java");
        when(resumes.findByIdAndCandidateIdAndDeletedFalse(501L,25L)).thenReturn(Optional.of(resume));
    }
    @Test void createUsesAuthenticatedCandidateAndSnapshotsResume() throws Exception {
        allowCreate();
        var dto=service.create(100L,createRequest(),AppLanguage.UZ);
        assertEquals(ApplicationStatus.NEW,dto.getStatus());assertEquals(501L,dto.getResumeId());
        assertEquals("Ali",dto.getFullName());assertNotNull(dto.getConsentAcceptedAt());
        var saved=org.mockito.ArgumentCaptor.forClass(JobApplication.class);
        verify(apps).save(saved.capture());
        assertEquals(25L,saved.getValue().getCandidateId());
        assertEquals("Java",new ObjectMapper().readTree(saved.getValue().getResumeSnapshot()).get("title").asText());
    }
    @Test void createRejectsMissingConsentBeforeDatabaseAccess(){
        for(Boolean consent:Arrays.asList(null,false)){
            var r=createRequest();r.setConsentAccepted(consent);
            assertThrows(AppBadException.class,()->service.create(100L,r,AppLanguage.UZ));
        }
        verifyNoInteractions(vacancies,resumes);verify(apps,never()).save(any());
    }
    @Test void createRejectsUnavailableVacancyForeignResumeAndDuplicate(){
        assertThrows(AppNotFoundException.class,()->service.create(100L,createRequest(),AppLanguage.UZ));
        allowCreate();
        for(VacancyStatus status:VacancyStatus.values()){
            if(status==VacancyStatus.PUBLISHED) continue;
            application.getVacancy().setVacancyStatus(status);
            assertThrows(AppConflictException.class,()->service.create(100L,createRequest(),AppLanguage.UZ));
        }
        application.getVacancy().setVacancyStatus(VacancyStatus.PUBLISHED);
        when(resumes.findByIdAndCandidateIdAndDeletedFalse(501L,25L)).thenReturn(Optional.empty());
        assertThrows(AppNotFoundException.class,()->service.create(100L,createRequest(),AppLanguage.UZ));
        allowCreate();
        when(apps.existsByVacancyIdAndCandidateIdAndDeletedFalse(100L,25L)).thenReturn(true);
        assertThrows(AppConflictException.class,()->service.create(100L,createRequest(),AppLanguage.UZ));
        verify(apps,never()).save(any());
    }
    @Test void sellerStatusAndListingRequireCompanyOwnership(){
        when(apps.findForChat(9001L)).thenReturn(Optional.of(application));
        when(vacancies.findByIdAndDeletedFalse(100L)).thenReturn(Optional.of(application.getVacancy()));
        when(companies.getOwnedCompanyIds(25L)).thenReturn(List.of(99L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.applicationStatusResponse(9001L,ApplicationResponseStatus.ACCEPTED,AppLanguage.UZ));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.getApplicationVacancy(100L,GetNewApplicationStatus.NEW,1,20));
        verify(apps,never()).save(any());
        when(companies.getOwnedCompanyIds(25L)).thenReturn(List.of(42L));
        assertEquals(ApplicationStatus.ACCEPTED,service.applicationStatusResponse(9001L,ApplicationResponseStatus.ACCEPTED,AppLanguage.UZ).getStatus());
        assertNotNull(application.getFirstResponseAt());
    }}