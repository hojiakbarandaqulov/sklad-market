package org.example.dto.internal.dashboard;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MonthlyCountResponse {
    private String month;
    private Long count;
}
