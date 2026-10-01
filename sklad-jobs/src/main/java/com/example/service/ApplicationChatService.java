package com.example.service;

import com.example.dto.application.ApplicationChatDTO;

public interface ApplicationChatService {
    ApplicationChatDTO open(Long applicationId);

    void deliver(Long applicationId);
}