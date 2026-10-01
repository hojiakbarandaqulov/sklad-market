package com.example.service.impl;

import com.example.config.clent.*;
import com.example.dto.application.*;
import com.example.entity.JobApplication;
import com.example.exp.AppNotFoundException;
import com.example.repository.JobApplicationRepository;
import com.example.service.ApplicationChatService;
import com.example.utils.SpringSecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationChatServiceImpl implements ApplicationChatService {
    private final JobApplicationRepository repository;
    private final ChatClient chatClient;
    private final CompanyClient companyClient;
    @Value("${jobs.chat.internal-token:}")
    private String token;

    @Override
    @Transactional
    public ApplicationChatDTO open(Long applicationId) {
        Long userId = SpringSecurityUtil.getProfileId();
        if (userId == null) throw new AccessDeniedException("Authentication required");
        JobApplication a = find(applicationId);
        if (!userId.equals(a.getCandidateId())) {
            var owned = companyClient.getOwnedCompanyIds(userId);
            if (owned == null || !owned.contains(a.getVacancy().getCompanyId()))
                throw new AccessDeniedException("Application access denied");
        }
        // Also allows an existing application to be linked on first request.
        attempt(a);
        return new ApplicationChatDTO(a.getId(), a.getChatThreadId(), a.getChatThreadId() == null ? "PENDING" : "READY");
    }

    @Override
    @Transactional
    public void deliver(Long applicationId) {
        var optional = repository.findForChat(applicationId);
        if (optional.isEmpty()) return;
        JobApplication a = optional.get();
        if (a.getChatThreadId() != null || !a.isChatPending() ||
                (a.getChatNextAttemptAt() != null && a.getChatNextAttemptAt().isAfter(Instant.now()))) return;
        attempt(a);
    }

    private JobApplication find(Long id) {
        return repository.findForChat(id).orElseThrow(() -> new AppNotFoundException("Application not found"));
    }

    private void attempt(JobApplication a) {
        if (a.getChatThreadId() != null) return;
        a.setChatPending(true);
        a.setChatNextAttemptAt(Instant.now().plusSeconds(60));
        if (token == null || token.isBlank()) return;
        try {
            var response = chatClient.open(token, new JobChatRequest(a.getId(), a.getVacancy().getId(),
                    a.getCandidateId(), a.getVacancy().getCompanyId()));
            Long threadId = response == null ? null : response.get("threadId");
            if (threadId == null || threadId <= 0) {
                log.warn("Chat service returned no thread ID for application {}", a.getId());
                return;
            }
            a.setChatThreadId(threadId);
            a.setChatPending(false);
            a.setChatNextAttemptAt(null);
        } catch (feign.FeignException e) {
            log.warn("Application {} chat delivery failed, HTTP {}", a.getId(), e.status());
        }
    }
}