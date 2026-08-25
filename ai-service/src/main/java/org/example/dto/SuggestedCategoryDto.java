package org.example.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SuggestedCategoryDto {
    private String slug;
    private String name;
}
