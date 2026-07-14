package org.example.dto.categoryAtribute;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryCreateRequest {
    private Long parentId;

    @NotBlank
    private String nameUz;

    @NotBlank
    private String nameRu;

    @NotBlank
    private String nameEn;

    @NotBlank
    private String slug;

    private Integer sortOrder;
    private Boolean isActive = Boolean.TRUE;
}