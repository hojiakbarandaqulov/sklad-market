package org.example.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ChatMessageResponse {
    private Long id;

    @JsonProperty("thread_id")
    private Long threadId;

    @JsonProperty("sender_id")
    private Long senderId;

    @JsonProperty("sender_type")
    private String senderType;

    private String body;

    @JsonProperty("attachment_key")
    private String attachmentKey;

    @JsonProperty("attachment_url")
    private String attachmentUrl;

    @JsonProperty("sent_at")
    private LocalDateTime sentAt;

    @JsonProperty("delivered_at")
    private LocalDateTime deliveredAt;

    @JsonProperty("read_at")
    private LocalDateTime readAt;

    /** Xabar holati: sent, delivered yoki read. */
    private String status;
}
