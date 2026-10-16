package com.example.service;

import com.example.dto.resume.AiJobsRequests;
import com.example.entity.*;
import com.example.entity.JobInvitation.Status;
import com.example.enums.*;
import com.example.exp.*;
import com.example.repository.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class JobInvitationServiceTest {
    JobInvitationRepository invitations=mock(JobInvitationRepository.class);
    VacancyRepository vacancies=mock(VacancyRepository.class);
    ResumeRepository resumes=mock(ResumeRepository.class);
    ResumeDiscoveryRepository discoveries=mock(ResumeDiscoveryRepository.class);
    JobApplicationRepository applications=mock(JobApplicationRepository.class);
    JobsVacancyAccess access=mock(JobsVacancyAccess.class);
    AiCandidateDiscoveryService discovery=mock(AiCandidateDiscoveryService.class);
    JobInvitationService service=new JobInvitationService(invitations,vacancies,resumes,discoveries,applications,access,discovery);
    Vacancy vacancy;
    AiJobsRequests.Invitation request;
    JobInvitation pending;
    @BeforeEach void setup() {
        when(access.viewer()).thenReturn(77L);
        vacancy=new Vacancy(); vacancy.setId(42L); vacancy.setBuyerId(77L); vacancy.setVacancyStatus(VacancyStatus.PUBLISHED);
        vacancy.setPositionName("Java developer");
        when(access.owned(42L,true,true)).thenReturn(vacancy);
        when(access.owned(42L,true,false)).thenReturn(vacancy);
        when(access.isOwner(vacancy,77L)).thenReturn(true);
        when(vacancies.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(vacancy));
        when(vacancies.findForApplication(42L)).thenReturn(Optional.of(vacancy));
        Resume resume=new Resume(); resume.setId(501L); resume.setCandidateId(88L);
        ResumeDiscovery profile=new ResumeDiscovery(); profile.setResumeId(501L); profile.setResume(resume);
        profile.setEnabled(true); profile.setSpecialty("Java developer");
        when(resumes.findByIdAndDeletedFalse(501L)).thenReturn(Optional.of(resume));
        when(discoveries.findById(501L)).thenReturn(Optional.of(profile));
        when(discovery.requireEligibleLocked(vacancy,501L,77L)).thenReturn(profile);
        when(invitations.saveAndFlush(any())).thenAnswer(i->{JobInvitation sent=i.getArgument(0);sent.setId(12L);return sent;});
        when(invitations.save(any())).thenAnswer(i->i.getArgument(0));
        request=new AiJobsRequests.Invitation(501L,42L,"Hello",UUID.randomUUID());
        pending=new JobInvitation();pending.setId(12L);pending.setVacancyId(42L);pending.setResumeId(501L);
        pending.setCandidateId(88L);pending.setInviterId(77L);pending.setStatus(Status.PENDING);
        pending.setRequestId(request.requestId().toString());pending.setMessage("Hello");
        pending.setCreatedAt(Instant.now());pending.setExpiresAt(Instant.now().plusSeconds(86400));
        when(invitations.findById(12L)).thenReturn(Optional.of(pending));
        when(invitations.findLocked(12L)).thenReturn(Optional.of(pending));
    }
    @Test void sendingOnlyCreatesPendingInvitationNeverApplicationOrChat() {
        var sent=service.send(request);
        assertEquals(Status.PENDING,sent.status()); assertEquals(501L,sent.resumeId());
        assertTrue(sent.expiresAt().isAfter(Instant.now().plusSeconds(13*86400)));
        verify(applications,never()).save(any());
        verify(invitations).saveAndFlush(any());
    }
    @Test void retryIsIdempotentAndChangedPayloadRejected() {
        when(invitations.findByInviterIdAndRequestId(77L,request.requestId().toString())).thenReturn(Optional.of(pending));
        assertEquals(12L,service.send(request).id());
        verify(invitations,never()).saveAndFlush(any());
        verifyNoInteractions(discovery,applications);
        assertThrows(AppConflictException.class,()->service.send(new AiJobsRequests.Invitation(501L,42L,"Changed",request.requestId())));
    }
    @Test void existingPendingOrSpamLimitCannotSend() {
        when(invitations.existsByVacancyIdAndCandidateIdAndStatus(42L,88L,Status.PENDING)).thenReturn(true);
        assertThrows(AppConflictException.class,()->service.send(request));
        when(invitations.existsByVacancyIdAndCandidateIdAndStatus(42L,88L,Status.PENDING)).thenReturn(false);
        when(invitations.countByInviterIdAndVacancyIdAndCreatedAtAfter(eq(77L),eq(42L),any())).thenReturn(5L);
        assertThrows(AppConflictException.class,()->service.send(request));
        verify(invitations,never()).saveAndFlush(any());
    }
    @Test void onlyInvitedAccountCanDeclineAndOnlyOwnerCanWithdraw() {
        assertThrows(AppNotFoundException.class,()->service.decline(12L));
        verify(invitations,never()).save(any());
        when(access.viewer()).thenReturn(88L);
        assertEquals(Status.DECLINED,service.decline(12L).status());
        assertEquals(Status.DECLINED,service.decline(12L).status());
        pending.setStatus(Status.PENDING);
        when(access.owned(42L,true,false)).thenThrow(new AppNotFoundException("not found"));
        assertThrows(AppNotFoundException.class,()->service.withdraw(12L));
    }
    @Test void applicationRequiresExactCandidateVacancyResumeAndLivePendingInvitation() {
        assertThrows(AppNotFoundException.class,()->service.validateApplication(12L,99L,vacancy,501L));
        assertThrows(AppNotFoundException.class,()->service.validateApplication(12L,88L,vacancy,502L));
        pending.setExpiresAt(Instant.now().minusSeconds(1));
        assertThrows(AppConflictException.class,()->service.validateApplication(12L,88L,vacancy,501L));
        pending.setExpiresAt(Instant.now().plusSeconds(100)); pending.setStatus(Status.WITHDRAWN);
        assertThrows(AppConflictException.class,()->service.validateApplication(12L,88L,vacancy,501L));
        pending.setStatus(Status.PENDING);
        assertSame(pending,service.validateApplication(12L,88L,vacancy,501L));
        assertEquals(Status.PENDING,pending.getStatus());
        service.completeApplication(pending);
        assertEquals(Status.ACCEPTED,pending.getStatus());
    }
    @Test void transferredVacancyCannotAcceptOldOwnersInvitation() {
        when(access.isOwner(vacancy,77L)).thenReturn(false);
        assertThrows(AppConflictException.class,()->service.validateApplication(12L,88L,vacancy,501L));
        verify(invitations,never()).save(any());
    }
    @Test void revokedProfileDoesNotLeakSpecialtyInRestoredInvitation() {
        when(invitations.findByInviterIdAndRequestId(77L,request.requestId().toString())).thenReturn(Optional.of(pending));
        discoveries.findById(501L).orElseThrow().setEnabled(false);
        var result=service.send(request);
        assertNull(result.specialty()); assertEquals(Status.EXPIRED,result.status());
    }
    @Test void lostVacancyOwnershipSuppressesProfessionalDetailsInOutbox() {
        when(invitations.findByInviterId(eq(77L),any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(pending)));
        when(access.owned(42L,false,false)).thenThrow(new AppNotFoundException("not found"));
        assertNull(service.list("outgoing",1,10).items().get(0).specialty());
    }
}
