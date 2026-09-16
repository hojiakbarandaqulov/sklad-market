package com.example.dto.vacancy;

import com.example.enums.ExperienceLevel;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class VacancyRequest {
    @NotNull(message = "{validation.position.name.required}")
    private String positionName;
    private BigDecimal price;
    @NotNull(message = "{validation.employment.type.required}")
    private String employmentType;
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
    @NotNull(message = "{validation.responsibilities.required}")
    private String responsibilities;
    @NotNull(message = "{validation.requirements.required}")
    private String requirements;
    @NotNull(message = "{validation.working.conditions.required}")
    private String workingConditions;
    private Boolean showContacts = false;
}
