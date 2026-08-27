package org.example.client.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyBranchSummaryResponse {
    private boolean exists;
    private Long id;
    private Long companyId;
    private String name;
    private String address;
    private String phone;
    private String lng;
    private String lat;
}
