package com.example.dto.application;
import com.example.enums.ApplicationStatus;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
@Getter @Setter
public class JobApplicationDTO {
    private Long id;
    private Long vacancyId;
    private String positionName;
    private Long companyId;
    private Long resumeId;
    private ApplicationStatus status;
    private String fullName;
    private String phone;
    private String email;
    private Long regionId;
    private BigDecimal expectedSalary;
    private String coverLetter;
    private Instant consentAcceptedAt;
    private Instant reviewedAt;
    private Instant withdrawnAt;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
}