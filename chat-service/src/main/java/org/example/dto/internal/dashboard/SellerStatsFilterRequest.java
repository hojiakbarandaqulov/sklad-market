package org.example.dto.internal.dashboard;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SellerStatsFilterRequest {
    private List<Long> companyIds;
    private Integer months;
}
