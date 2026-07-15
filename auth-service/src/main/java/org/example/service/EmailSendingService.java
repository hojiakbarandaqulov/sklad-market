package org.example.service;

import org.example.enums.AppLanguage;

public interface EmailSendingService {

     void sendRegistrationEmail(String email);

     void sentResetPasswordEmail(String email, AppLanguage language);

}
