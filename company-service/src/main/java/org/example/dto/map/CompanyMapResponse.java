package org.example.dto.map;

import lombok.Getter;
import lombok.Setter;
import org.example.enums.VerificationStatus;

import java.time.LocalDate;

@Getter
@Setter
public class CompanyMapResponse {
    // null means the product count is temporarily unavailable.
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    private Long productCount;

    private Long companyId;
    private String companyName;
    private String companyAddress;
    private String slug;
    private String lng;
    private String lat;
    private String logoUrl;
    private VerificationStatus verificationStatus;
}
