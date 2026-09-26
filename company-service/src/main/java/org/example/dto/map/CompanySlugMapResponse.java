package org.example.dto.map;

import lombok.Getter;
import lombok.Setter;
import org.example.enums.VerificationStatus;

import java.time.LocalDate;

@Getter
@Setter
public class CompanySlugMapResponse {
    // null means the product count is temporarily unavailable.
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    private Long productCount;

    private Long id;
    private String name;
    private String slug;
    private VerificationStatus status;
    private Long regionId;
    private Long districtId;
    private String address;
    private String phonePrimary;
    private String phoneSecondary;
    private String website;
    private LocalDate companyCreatedDate;
    private String lat;
    private String lng;
}
