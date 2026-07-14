package org.example.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.enums.VerificationStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class CompanyInfoDTO {
    private Long id;
    private String name;
    private String slug;
    private String logoUrl;
    private VerificationStatus verificationStatus;
    private Boolean isBlocked;
    private LocalDate companyCreatedDate;
}
