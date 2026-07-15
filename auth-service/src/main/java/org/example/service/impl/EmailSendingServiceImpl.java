package org.example.service.impl;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.example.enums.AppLanguage;
import org.example.enums.EmailType;
import org.example.exp.AppBadException;
import org.example.repository.EmailHistoryRepository;
import org.example.service.EmailHistoryService;
import org.example.service.EmailSendingService;
import org.example.service.ResourceBundleService;
import org.example.utils.JwtUtil;
import org.example.utils.RandomUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class EmailSendingServiceImpl implements EmailSendingService {

    @Value("${spring.mail.username}")
    private String fromAccount;

    @Value("${server.domain}")
    private String serverDomain;

    @Value("${server.port}")
    private String serverPort;

    private final EmailHistoryService emailHistoryService;
    private final EmailHistoryRepository emailHistoryRepository;
    private final JavaMailSender mailSender;
    private final ResourceBundleService messageService;


    @Override
    public void sendRegistrationEmail(String email, AppLanguage language) {
        String code = RandomUtil.getRandomCode();
        String subject = "Complete registration";
        String body = "How are you. This is confirm code registration  send code: " + code;
        sendMimeEmail(email, subject, body);
        emailHistoryService.create(email, code, EmailType.RESET_PASSWORD);
        checkAndSendMineEmail(email,subject,body,code,language);
    }

    // We will continue this code in the reset API
    public void sentResetPasswordEmail(String username, AppLanguage language) {
        String code = RandomUtil.getRandomCode();
        String subject = "Reset password Conformation";
        String body = "How are you. This is confirm code reset password send code: " + code;
        checkAndSendMineEmail(username, subject, body, code,language);
    }

    private void checkAndSendMineEmail(String email, String subject, String body, String code, AppLanguage language) {
        Long count = emailHistoryService.getEmailCount(email);
        if (count >= 3) {
            throw new AppBadException(messageService.getMessage("email.reached.sms",language));
        }

        sendMimeEmail(email, subject, body);
        emailHistoryService.create(email, code, EmailType.RESET_PASSWORD);
    }


    private void sendMimeEmail(String email, String subject, String body) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            msg.setFrom(fromAccount);
            MimeMessageHelper helper = new MimeMessageHelper(msg, true);
            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(body, true);
            CompletableFuture.runAsync(() -> {
                mailSender.send(msg);
            });
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }
}
