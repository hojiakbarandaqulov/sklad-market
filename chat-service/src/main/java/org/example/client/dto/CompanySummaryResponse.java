package org.example.client.dto;

import lombok.Builder;
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
