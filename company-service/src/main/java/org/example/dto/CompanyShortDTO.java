package org.example.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.example.enums.VerificationStatus;

import java.time.LocalDateTime;

@Getter
@Setter
public class CompanyShortDTO {

    private Long id;
    private String name;
    private String slug;
    private String logoUrl;
    private Long productCount;
    private VerificationStatus verificationStatus;
    private Boolean isBlocked;
    private LocalDateTime createdAt;
}
