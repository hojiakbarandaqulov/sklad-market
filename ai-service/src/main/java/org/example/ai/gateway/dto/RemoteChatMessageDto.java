package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors chat-service's {@code ChatMessageResponse} (verified in source: snake_case via {@code @JsonProperty}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteChatMessageDto(
        Long id,
        @JsonProperty("thread_id") Long threadId,
        @JsonProperty("sender_id") Long senderId,
        @JsonProperty("sender_type") String senderType,
        String body,
        @JsonProperty("sent_at") String sentAt) {
}
