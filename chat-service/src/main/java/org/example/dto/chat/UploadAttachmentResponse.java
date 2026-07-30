package org.example.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UploadAttachmentResponse {
    /** WebSocket orqali xabar yuborishda attachment_key sifatida yuboriladi. */
    @JsonProperty("attachment_key")
    private String attachmentKey;

    /** Frontend fayl yoki rasmni ko'rsatishi uchun tayyor URL. */
    @JsonProperty("attachment_url")
    private String attachmentUrl;
}
