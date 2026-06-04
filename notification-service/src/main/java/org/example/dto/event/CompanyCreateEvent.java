package org.example.dto.event;

import lombok.Getter;
import lombok.Setter;
import org.example.enums.VerificationStatus;

import java.time.LocalDateTime;

@Getter
@Setter
public class CompanyCreateEvent {
    private Long companyId;
    private Long ownerUserId;
    private String companyName;
    private String companySlug;
    private VerificationStatus verificationStatus;
    private LocalDateTime createdDate;
}
