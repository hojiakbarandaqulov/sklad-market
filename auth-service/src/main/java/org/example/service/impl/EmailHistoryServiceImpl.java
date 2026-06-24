package org.example.service.impl;

import org.example.entity.EmailHistory;
import org.example.enums.EmailType;
import org.example.exp.AppBadException;
import org.example.repository.EmailHistoryRepository;
import org.example.service.EmailHistoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class EmailHistoryServiceImpl implements EmailHistoryService {

    private final EmailHistoryRepository emailHistoryRepository;

    public EmailHistoryServiceImpl(EmailHistoryRepository emailHistoryRepository) {
        this.emailHistoryRepository = emailHistoryRepository;
    }

    @Override
    public void create(String email, String code, EmailType emailType){
        EmailHistory emailHistoryEntity = new EmailHistory();
        emailHistoryEntity.setEmail(email);
        emailHistoryEntity.setCode(code);
        emailHistoryEntity.setEmailType(emailType);
        emailHistoryEntity.setAttemptCount(0);
        emailHistoryEntity.setCreatedDate(LocalDateTime.now());
        emailHistoryRepository.save(emailHistoryEntity);
    }

    public Long getEmailCount(String email){
        LocalDateTime now = LocalDateTime.now();
        return emailHistoryRepository.countByEmailAndCreatedDateBetween(email,now.minusMinutes(1),now);
    }

    public void check(String email, String code){
        Optional<EmailHistory> optional = emailHistoryRepository.findTop1ByEmailAndEmailTypeOrderByCreatedDateDesc(email, EmailType.RESET_PASSWORD);
        if(optional.isEmpty()){
           throw new AppBadException("verification failed");
        }
        EmailHistory entity = optional.get();
        if (entity.getAttemptCount()>=3){
            throw new AppBadException("verification failed");
        }
        if (!entity.getCode().equals(code)){
            emailHistoryRepository.updateAttemptCount(entity.getId());
            throw new AppBadException("verification failed");
        }

        LocalDateTime expDate=entity.getCreatedDate().plusMinutes(15);
        if (LocalDateTime.now().isAfter(expDate)){
            throw new AppBadException("verification.failed");
        }
    }
}
