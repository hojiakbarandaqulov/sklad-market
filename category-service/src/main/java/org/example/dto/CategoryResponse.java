package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryResponse {
    private Long id;
    private String nameUz;
    private String nameRu;
    private String nameEn;
    private String slug;
    private String iconId;
    private String iconUrl;
    private Integer sortOrder;
    private Boolean isActive = Boolean.TRUE;
}
