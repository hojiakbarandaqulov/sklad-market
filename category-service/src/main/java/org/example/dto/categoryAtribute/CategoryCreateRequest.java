package org.example.dto.categoryAtribute;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryCreateRequest {
    private Long parentId;

    @NotBlank(message = "nameUz required")
    private String nameUz;

    @NotBlank(message = "nameRu required")
    private String nameRu;

    @NotBlank(message = "nameEn required")
    private String nameEn;

    @NotBlank(message = "slug required")
    private String slug;

    private Integer sortOrder;
    private Boolean isActive = Boolean.TRUE;
}