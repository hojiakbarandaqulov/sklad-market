package com.example.service;

import com.example.dto.resume.AiJobsRequests;
import com.example.entity.JobInvitation;
import com.example.entity.JobInvitation.Status;
import com.example.entity.Vacancy;
import java.time.Instant;
import java.util.List;

public interface JobInvitationService {
    Invitation send(AiJobsRequests.Invitation request);

    InvitationPage list(String direction,int page,int perPage);

    Invitation decline(Long id);

    Invitation withdraw(Long id);

    JobInvitation validateApplication(Long invitationId,Long candidateId,Vacancy vacancy,Long resumeId);

    void completeApplication(JobInvitation invitation);

    record Invitation(Long id,Long vacancyId,Long resumeId,String positionName,String specialty,
            Status status,String message,Instant expiresAt,Instant createdAt) {}

    record InvitationPage(List<Invitation> items,int page,int perPage,boolean hasMore) {}
}
