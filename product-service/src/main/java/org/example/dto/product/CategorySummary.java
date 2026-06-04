package org.example.dto.product;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public  class CategorySummary {
    private Long id;
    private String name;
    private String slug;
}
