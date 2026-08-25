package org.example.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class MessageDto {
    private UUID id;
    private String role;
    private String content;
    private String toolName;
    private String toolPayload;
    private Integer tokensIn;
    private Integer tokensOut;
    private Instant createdAt;
}
