package org.example.dto.support;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.example.enums.SupportParticipantRole;

import java.time.LocalDateTime;

@Getter
@Setter
public class SupportMessageResponse {
    private Long id;

    @JsonProperty("thread_id")
    private Long threadId;

    @JsonProperty("sender_id")
    private Long senderId;

    @JsonProperty("sender_role")
    private SupportParticipantRole senderRole;

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

    private String status;
}
