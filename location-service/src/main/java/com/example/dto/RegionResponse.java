package com.example.dto;

import com.example.enums.RegionType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RegionResponse {
    private Long id;
    private String code;
    private String name;
    private String nameUz;
    private String nameRu;
    private String nameEn;
    private RegionType type;
    private Integer sortOrder;
}
