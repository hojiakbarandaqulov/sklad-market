package org.example.dto.internal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CompanyBranchInternalResponse {
    private boolean exists;
    private Long id;
    private Long companyId;
    private String name;
    private String address;
    private String phone;
    private String lng;
    private String lat;
}
