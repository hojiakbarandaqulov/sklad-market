package com.example.dto.resume;

import com.example.enums.ExperienceLevel;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public final class AiJobsRequests {
    private AiJobsRequests() {}
    public record Discovery(@NotNull Boolean enabled, Boolean consentAccepted,
            @Size(max=120) String specialty, ExperienceLevel experienceLevel, @Positive Long regionId,
            @Size(max=20) List<@NotBlank @Size(max=60) String> skills) {}
    public record Invitation(@NotNull @Positive Long resumeId, @NotNull @Positive Long vacancyId,
            @Size(max=1000) String message, @NotNull UUID requestId) {}
}
