package org.example.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatParticipantResponse {
    private Long id;
    /** Qiymati "company" yoki "user" bo'ladi. */
    private String type;

    @JsonProperty("display_name")
    private String displayName;

    private String username;
    private String slug;

    @JsonProperty("avatar_url")
    private String avatarUrl;
}
