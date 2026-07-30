package org.example.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatCreateResponse {
    /** Yaratilgan yoki topilgan chatning ID raqami. */
    @JsonProperty("thread_id")
    private Long threadId;

    /** true bo'lsa yangi chat yaratildi, false bo'lsa avvalgi chat qayta ochildi. */
    @JsonProperty("is_new")
    private boolean isNew;
}
