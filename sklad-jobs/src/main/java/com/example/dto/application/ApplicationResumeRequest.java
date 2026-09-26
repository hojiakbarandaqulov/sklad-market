package com.example.dto.application;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationResumeRequest {
    @NotNull
    @Positive
    private Long resumeId;
}