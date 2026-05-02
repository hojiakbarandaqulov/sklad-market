package org.example.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.enums.VerificationStatus;

import java.time.LocalDateTime;

@Getter
@Setter
public class CompanyResponseDTO {
    private Long id;
    private String name;
    private String slug;
    private String shortDescription;
    private String description;
    private String logoUrl;
    private String coverUrl;
    private String stir;
    private String phonePrimary;
    private String phoneSecondary;
    private String website;
    private Long regionId;
    private Long districtId;
    private String address;
    private VerificationStatus verificationStatus;
    private String lng;
    private String lat;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
}
