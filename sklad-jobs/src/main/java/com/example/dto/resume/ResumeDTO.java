package com.example.dto.resume;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class ResumeDTO {
    private Long id;
    private Long candidateId;
    private String fileId;
    private String title;
    private String fullName;
    private String phone;
    private String email;
    private Long regionId;
    private String desiredPosition;
    private BigDecimal expectedSalary;
    private String aboutMe;
    private String skills;
    private String workExperience;
    private String education;
    private String languages;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
}