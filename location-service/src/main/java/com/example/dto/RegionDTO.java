package com.example.dto;

import com.example.enums.RegionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegionDTO {

    @NotBlank(message = "{region.code.required}")
    @Size(max = 50, message = "{region.code.size}")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "{region.code.pattern}")
    private String code;

    @NotBlank(message = "{region.name.uz.required}")
    @Size(max = 100, message = "{region.name.size}")
    private String nameUz;

    @NotBlank(message = "{region.name.ru.required}")
    @Size(max = 100, message = "{region.name.size}")
    private String nameRu;

    @NotBlank(message = "{region.name.en.required}")
    @Size(max = 100, message = "{region.name.size}")
    private String nameEn;

    @NotNull(message = "{region.type.required}")
    private RegionType type;

    @NotNull(message = "{region.sort.order.required}")
    @PositiveOrZero(message = "{region.sort.order.positive}")
    private Integer sortOrder = 0;
}
