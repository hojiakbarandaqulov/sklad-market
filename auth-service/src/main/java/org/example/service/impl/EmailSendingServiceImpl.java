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


    @Async
    @Override
    public void sendRegistrationEmail(String email, Long profileId) {
        String subject = "Complete registration";

        String verificationToken = JwtUtil.encode(profileId);

        String verificationUrl = serverDomain + "/api/v1/auth/verification/" + verificationToken;

        String body = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>Complete Registration</title>
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            background-color: #f4f4f4;
                            padding: 20px;
                        }
                        .button {
                            padding: 10px 30px;
                            display: inline-block;
                            text-decoration: none;
                            color: white;
                            background-color: indianred;
                            border-radius: 5px;
                            margin: 10px 0;
                        }
                        .button:hover {
                            background-color: #dd4444;
                        }
                    </style>
                </head>
                <body>
                    <h1>Complete registration verification</h1>
                    <p>Please click the button to complete registration:</p>
                    <a class="button" href="%s" target="_blank">Click here</a>
                </body>
                </html>
                """;
        body = String.format(body, verificationUrl);


        sendMimeEmail(email, subject, body);
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
