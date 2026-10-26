package com.example.dto.internal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyOwnershipResponse {
    private Long companyId;
    private boolean exists;
    private boolean owner;
    private boolean active;
}
