package com.example.dto.vacancy;

import com.example.enums.ExperienceLevel;
import com.example.enums.JobType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VacancyRequest {
    @Positive
    private Long categoryId;
    @NotNull(message = "{validation.position.name.required}")
    private String positionName;
    private BigDecimal price;
    @NotNull(message = "{validation.employment.type.required}")
    private String employmentType;

    private JobType jobType;
    @NotNull(message = "{validation.work.schedule.required}")
    private String workSchedule;
    @NotNull(message = "{validation.short.description.required}")
    private String shortDescription;
    @NotNull(message = "{validation.region.id.required}")
    private Long regionId;
    private String address;
    private String lng;
    private String lat;
    @NotNull(message = "{validation.experience.level.required}")
    private ExperienceLevel experienceLevel;
    @NotNull(message = "{validation.requirements.required}")
    private String requirements;
    @NotNull(message = "{validation.working.conditions.required}")
    private String workingConditions;
    private Boolean showContacts = false;
}
