package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyRequestDTO {
    @NotBlank(message = "name required")
    private String name;

    private String shortDescription;
    private String description;

    @NotBlank(message = "stir required")
    private String stir;

    @NotBlank(message = "phonePrimary required")
    private String phonePrimary;

    private String phoneSecondary;
    private String website;

    private Long regionId;

    private Long districtId;

    @NotBlank(message = "address required")
    private String address;

    @NotBlank(message = "lng required")
    private String lng;

    @NotBlank(message = "lat required")
    private String lat;
}