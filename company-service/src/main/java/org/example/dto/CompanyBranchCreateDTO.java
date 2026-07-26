package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyBranchCreateDTO {
    @NotBlank(message = "name required")
    private String name;

    @NotBlank(message = "address required")
    private String address;
    private String phone;
    private String lng;
    private String lat;
}
