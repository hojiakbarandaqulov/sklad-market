package com.example.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobCategoryRequest {
    @NotBlank @Size(max = 255)
    private String nameUz;
    @NotBlank @Size(max = 255)
    private String nameRu;
    @NotBlank @Size(max = 255)
    private String nameEn;
}
