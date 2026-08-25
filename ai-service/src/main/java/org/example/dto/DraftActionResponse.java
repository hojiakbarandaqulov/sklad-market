package org.example.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class DraftActionResponse {
    private UUID draftId;
    private String status;
    private Long leadId;
}
