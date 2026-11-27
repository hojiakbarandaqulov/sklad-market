package com.example.service;

import com.example.dto.resume.AiJobsRequests;
import com.example.entity.*;
import com.example.entity.JobInvitation.Status;
import com.example.enums.VacancyStatus;
import com.example.exp.*;
import com.example.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class JobInvitationService {
    private final JobInvitationRepository invitations;
    private final VacancyRepository vacancies;
    private final ResumeRepository resumes;
    private final ResumeDiscoveryRepository discoveries;
    private final JobApplicationRepository applications;
    private final JobsVacancyAccess access;
    private final AiCandidateDiscoveryService discovery;

    public record Invitation(Long id,Long vacancyId,Long resumeId,String positionName,String specialty,
            Status status,String message,Instant expiresAt,Instant createdAt) {}
    public record InvitationPage(List<Invitation> items,int page,int perPage,boolean hasMore) {}

    @Transactional
    public Invitation send(AiJobsRequests.Invitation request) {
        long inviter = access.viewer();
        // A vacancy lock serializes duplicate and per-vacancy spam checks (including retries).
        Vacancy vacancy = access.owned(request.vacancyId(),true,true);
        var previous = invitations.findByInviterIdAndRequestId(inviter,request.requestId().toString());
        if (previous.isPresent()) {
            JobInvitation existing = previous.get();
            if (!Objects.equals(existing.getVacancyId(),request.vacancyId())
                    || !Objects.equals(existing.getResumeId(),request.resumeId())
                    || !Objects.equals(existing.getMessage(),cleanMessage(request.message())))
                throw new AppConflictException("Request ID was already used for a different invitation");
            return dto(existing,true);
        }
        ResumeDiscovery candidate = discovery.requireEligibleLocked(vacancy,request.resumeId(),inviter);
        if (applications.existsByVacancyIdAndCandidateIdAndDeletedFalse(vacancy.getId(),candidate.getResume().getCandidateId()))
            throw new AppConflictException("Candidate has already applied to this vacancy");
        Instant now = Instant.now();
        invitations.expirePending(vacancy.getId(),now,Status.PENDING,Status.EXPIRED);
        if (invitations.existsByVacancyIdAndCandidateIdAndStatus(vacancy.getId(),candidate.getResume().getCandidateId(),Status.PENDING))
            throw new AppConflictException("An invitation is already pending");
        if (invitations.countByInviterIdAndVacancyIdAndCreatedAtAfter(inviter,vacancy.getId(),now.minusSeconds(60)) >= 5
                || invitations.countByInviterIdAndVacancyIdAndCreatedAtAfter(inviter,vacancy.getId(),now.minusSeconds(86400)) >= 30
                || invitations.countByInviterIdAndCreatedAtAfter(inviter,now.minusSeconds(86400)) >= 60)
            throw new AppConflictException("Invitation limit reached; try later");
        JobInvitation invitation = new JobInvitation();
        invitation.setInviterId(inviter); invitation.setCandidateId(candidate.getResume().getCandidateId());
        invitation.setResumeId(request.resumeId()); invitation.setVacancyId(vacancy.getId());
        invitation.setRequestId(request.requestId().toString()); invitation.setMessage(cleanMessage(request.message()));
        invitation.setStatus(Status.PENDING); invitation.setCreatedAt(now); invitation.setExpiresAt(now.plus(Duration.ofDays(14)));
        return dto(invitations.saveAndFlush(invitation),true);
    }

    public InvitationPage list(String direction,int page,int perPage) {
        AiCandidateDiscoveryService.bounds(page,perPage);
        long viewer = access.viewer();
        Pageable paging=PageRequest.of(page-1,perPage,Sort.by(Sort.Direction.DESC,"id"));
        Page<JobInvitation> source = switch(direction) {
            case "incoming" -> invitations.findByCandidateId(viewer,paging);
            case "outgoing" -> invitations.findByInviterId(viewer,paging);
            default -> throw new AppBadException("Invalid invitation direction");
        };
        Map<Long,Boolean> permissions=new HashMap<>();
        return new InvitationPage(source.getContent().stream().map(i -> {
            boolean mayView="incoming".equals(direction) || permissions.computeIfAbsent(i.getVacancyId(),vacancyId -> {
                try {access.owned(vacancyId,false,false);return true;}
                catch(org.springframework.security.access.AccessDeniedException | AppNotFoundException denied) {return false;}
            });
            return dto(i,mayView);
        }).toList(),page,perPage,source.hasNext());
    }

    @Transactional
    public Invitation decline(Long id) {
        long viewer=access.viewer();
        JobInvitation invitation=read(id);
        if (!Long.valueOf(viewer).equals(invitation.getCandidateId())) throw notFound();
        lockVacancy(invitation.getVacancyId());
        invitation=invitations.findLocked(id).orElseThrow(JobInvitationService::notFound);
        if (!Long.valueOf(viewer).equals(invitation.getCandidateId())) throw notFound();
        if (invitation.getStatus()==Status.DECLINED) return dto(invitation,true);
        requirePending(invitation);
        invitation.setStatus(Status.DECLINED);
        return dto(invitations.save(invitation),true);
    }

    @Transactional
    public Invitation withdraw(Long id) {
        JobInvitation invitation=read(id);
        access.owned(invitation.getVacancyId(),true,false);
        invitation=invitations.findLocked(id).orElseThrow(JobInvitationService::notFound);
        if (invitation.getStatus()==Status.WITHDRAWN) return dto(invitation,true);
        requirePending(invitation);
        invitation.setStatus(Status.WITHDRAWN);
        return dto(invitations.save(invitation),true);
    }

    /** Called inside the existing application transaction, after its vacancy lock and owner checks. */
    @Transactional
    public JobInvitation validateApplication(Long invitationId,Long candidateId,Vacancy vacancy,Long resumeId) {
        JobInvitation invitation=invitations.findLocked(invitationId).orElseThrow(JobInvitationService::notFound);
        if (!Objects.equals(invitation.getCandidateId(),candidateId) || !Objects.equals(invitation.getVacancyId(),vacancy.getId())
                || !Objects.equals(invitation.getResumeId(),resumeId)) throw notFound();
        requirePending(invitation);
        if (Boolean.TRUE.equals(vacancy.getDeleted()) || vacancy.getVacancyStatus()!=VacancyStatus.PUBLISHED
                || !access.isOwner(vacancy,invitation.getInviterId()))
            throw new AppConflictException("Invitation vacancy is no longer available");
        discovery.requireEligibleLocked(vacancy,resumeId,invitation.getInviterId());
        return invitation;
    }

    @Transactional
    public void completeApplication(JobInvitation invitation) {
        invitation.setStatus(Status.ACCEPTED);
        invitations.save(invitation);
    }

    private Invitation dto(JobInvitation i,boolean mayViewProfessionalProfile) {
        var vacancy=vacancies.findByIdAndDeletedFalse(i.getVacancyId()).orElse(null);
        // Never reveal stale professional details after discovery consent is withdrawn.
        var setting=discoveries.findById(i.getResumeId()).orElse(null);
        var resume=resumes.findByIdAndDeletedFalse(i.getResumeId()).orElse(null);
        String specialty=mayViewProfessionalProfile && setting!=null && setting.isEnabled() && resume!=null ? setting.getSpecialty() : null;
        Status status=i.getStatus();
        if (status==Status.PENDING && (!i.getExpiresAt().isAfter(Instant.now()) || vacancy==null
                || vacancy.getVacancyStatus()!=VacancyStatus.PUBLISHED || resume==null || setting==null || !setting.isEnabled()))
            status=Status.EXPIRED;
        return new Invitation(i.getId(),i.getVacancyId(),i.getResumeId(),vacancy==null?null:vacancy.getPositionName(),
                specialty,status,i.getMessage(),i.getExpiresAt(),i.getCreatedAt());
    }
    private JobInvitation read(Long id) {
        if(id==null || id<=0) throw notFound();
        return invitations.findById(id).orElseThrow(JobInvitationService::notFound);
    }
    private Vacancy lockVacancy(Long id) {
        return vacancies.findForApplication(id).orElseThrow(JobInvitationService::notFound);
    }
    private void requirePending(JobInvitation i) {
        if (i.getStatus()!=Status.PENDING || !i.getExpiresAt().isAfter(Instant.now()))
            throw new AppConflictException("Invitation is no longer pending");
    }
    private static String cleanMessage(String message) {
        if(message==null || message.isBlank()) return null;
        if(message.length()>1000) throw new AppBadException("Message is too long");
        return message.trim();
    }
    private static AppNotFoundException notFound() {return new AppNotFoundException("Invitation not found");}
}
