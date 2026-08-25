package org.example.ai.gateway.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors chat-service's {@code ChatThreadResponse} (verified in source: snake_case via {@code @JsonProperty}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteChatThreadDto(
        @JsonProperty("thread_id") Long threadId,
        @JsonProperty("other_party") RemoteChatParticipant otherParty,
        @JsonProperty("last_message") RemoteChatLastMessage lastMessage,
        @JsonProperty("unread_count") long unreadCount) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteChatParticipant(Long id, String type, @JsonProperty("display_name") String displayName, String slug) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RemoteChatLastMessage(String body, @JsonProperty("sent_at") String sentAt) {
    }
}
