package com.example.dto.internal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyBranchResponse {
    private boolean exists;
    private Long id;
    private Long companyId;
}
