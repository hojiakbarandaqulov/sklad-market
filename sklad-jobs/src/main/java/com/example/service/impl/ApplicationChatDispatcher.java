package com.example.service.impl;
import com.example.repository.JobApplicationRepository;
import com.example.service.ApplicationChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
@Component @RequiredArgsConstructor @Slf4j
public class ApplicationChatDispatcher {
    private final JobApplicationRepository repository;
    private final ApplicationChatService service;
    @Scheduled(fixedDelayString="${jobs.chat.retry-delay-ms:10000}",initialDelayString="${jobs.chat.retry-delay-ms:10000}")
    public void dispatch() {
        for(Long id:repository.findPendingChats(Instant.now(),PageRequest.of(0,20))) {
            try { service.deliver(id); }
            catch(RuntimeException e) { log.warn("Chat dispatch failed for application {} ({})",id,e.getClass().getSimpleName()); }
        }
    }
}