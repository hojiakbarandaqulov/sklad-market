package com.example.dto.warehouse;

import com.example.enums.WarehouseStatus;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WarehouseFilter {
    @NotNull
    @Positive
    private Long companyId;

    @Positive
    private Long companyBranchId;

    private WarehouseStatus status;

    @Size(max = 160)
    private String q;

    @Min(1)
    private int page = 1;

    @Min(1)
    @Max(100)
    private int perPage = 20;
}
