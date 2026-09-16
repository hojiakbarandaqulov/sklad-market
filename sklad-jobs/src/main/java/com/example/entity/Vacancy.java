package com.example.entity;

import com.example.entity.base.BaseEntity;
import com.example.enums.ExperienceLevel;
import com.example.enums.VacancyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
public class Vacancy extends BaseEntity {
    private String positionName;
    private Long companyId;
    private BigDecimal price;
    private String employmentType;
    private String workSchedule;
    private String shortDescription;
    private Long viewsCountCache = 0L;
    @Enumerated(EnumType.STRING)
    private VacancyStatus vacancyStatus;
    private Long regionId;
    private String address;
    private String lng;
    private String lat;
    @Enumerated(EnumType.STRING)
    private ExperienceLevel experienceLevel;
    @Column(columnDefinition = "TEXT")
    private String responsibilities;
    @Column(columnDefinition = "TEXT")
    private String requirements;
    @Column(columnDefinition = "TEXT")
    private String workingConditions;
    private Boolean showContacts=false;
    private Instant publishedAt;

}
