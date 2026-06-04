package org.example.dto.internal;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanySummaryResponse {
    private Long id;
    private Long ownerUserId;
    private String name;
    private String slug;
    private String logoPath;
}