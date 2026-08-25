package org.example.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ConversationDto {
    private UUID id;
    private String title;
    private String locale;
    private Instant createdAt;
    private Instant updatedAt;
}
