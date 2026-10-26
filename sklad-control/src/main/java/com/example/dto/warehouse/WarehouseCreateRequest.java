package com.example.dto.warehouse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WarehouseCreateRequest {
    @NotNull
    @Positive
    private Long companyId;

    @Positive
    private Long companyBranchId;

    @NotBlank
    @Size(max = 160)
    private String name;

    @Positive
    private Long regionId;

    @Size(max = 1000)
    private String address;
}
