package com.example.dto.vacancy;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VacancyModerationComment {
    @NotBlank(message = "comment required")
    private String comment;
}
