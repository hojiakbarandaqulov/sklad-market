package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryUpdateRequest {
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