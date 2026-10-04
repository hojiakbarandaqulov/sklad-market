package org.example.service.impl;
import lombok.RequiredArgsConstructor;
import org.example.dto.chat.JobChatRequest;
import org.example.repository.ChatThreadRepository;
import org.example.service.JobChatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service @RequiredArgsConstructor
public class JobChatServiceImpl implements JobChatService {
    private final ChatThreadRepository repository;

    @Override @Transactional
    public Long open(JobChatRequest r) {
        // Unique application_id + ON CONFLICT makes retries and concurrent delivery safe.
        repository.insertJobThread(r.getApplicationId(),r.getVacancyId(),r.getCandidateId(),r.getCompanyId());
        var thread=repository.findByApplicationId(r.getApplicationId()).orElseThrow();
        if(Boolean.TRUE.equals(thread.getDeleted()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Job chat is blocked");
        if(!r.getVacancyId().equals(thread.getVacancyId()) || !r.getCandidateId().equals(thread.getBuyerId())
                || !r.getCompanyId().equals(thread.getSellerCompanyId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Application chat context does not match");
        return thread.getId();
    }
}