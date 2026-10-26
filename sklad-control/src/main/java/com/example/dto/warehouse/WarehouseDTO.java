package com.example.dto.warehouse;

import com.example.enums.WarehouseStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WarehouseDTO {
    private Long id;
    private Long companyId;
    private Long companyBranchId;
    private String name;
    private Long regionId;
    private String address;
    private WarehouseStatus status;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;
}
