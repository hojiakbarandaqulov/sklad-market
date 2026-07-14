package org.example.dto.internal;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SellerProductStatsFilterRequest {
    private List<Long> companyIds;
    private Integer months;
    private Integer limit;
}
