package org.example.dto.map;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyLocationUpdate {
    @NotBlank(message = "address required")
    private String address;
    private String lat;
    private String lng;
}
