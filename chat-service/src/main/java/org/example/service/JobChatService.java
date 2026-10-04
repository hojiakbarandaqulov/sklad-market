package org.example.service;
import org.example.dto.chat.JobChatRequest;
public interface JobChatService {
    Long open(JobChatRequest request);
}