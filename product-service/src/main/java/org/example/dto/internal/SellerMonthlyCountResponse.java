package org.example.dto.internal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SellerMonthlyCountResponse {
    private String month;
    private Long count;
}
