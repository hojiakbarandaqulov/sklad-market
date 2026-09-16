package com.example.entity;

import com.example.entity.base.BaseEntity;
import com.example.enums.ApplicationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "job_application", indexes = {
        @Index(name = "idx_job_application_vacancy_status", columnList = "vacancy_id,status"),
        @Index(name = "idx_job_application_candidate", columnList = "candidate_id")
})
public class JobApplication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vacancy_id", nullable = false)
    private Vacancy vacancy;

    // Taken from the authenticated candidate, not from the request body.
    @Column(nullable = false)
    private Long candidateId;

    // Contact details as submitted with this application.
    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Long regionId;

    @Column(precision = 19, scale = 2)
    private BigDecimal expectedSalary;

    @Column(columnDefinition = "TEXT")
    private String coverLetter;

    // Tizimda yaratilgan Resume yozuvining ID si; fayl ID si emas.
    @Column(nullable = false)
    private Long resumeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationStatus status = ApplicationStatus.NEW;

    // Set by the server only after the candidate explicitly accepts consent.
    @Column(nullable = false)
    private Instant consentAcceptedAt;

    private Instant reviewedAt;
    private Instant withdrawnAt;

    // Ariza kelgan manba: masalan, SKLAD_JOBS yoki COMPANY_PROFILE.
    private String source;
    // Ushbu ariza bilan ishlashga tayinlangan HR yoki menejerning user ID si.
    private Long assignedHrId;
    // Nomzod va kompaniya o'rtasidagi eng oxirgi aloqa vaqti.
    private Instant lastContactAt;
    // Kompaniya nomzodga birinchi javob bergan vaqt; javob tezligini hisoblash uchun.
    private Instant firstResponseAt;
}