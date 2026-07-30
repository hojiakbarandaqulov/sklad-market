package org.example.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ChatLastMessageResponse {
    private Long id;
    private String body;

    @JsonProperty("attachment_url")
    private String attachmentUrl;

    @JsonProperty("sent_at")
    private LocalDateTime sentAt;

    /** Xabar holati: sent, delivered yoki read. */
    private String status;
}
