package com.example.dto.warehouse;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/** PATCH'da yuborilmagan maydon saqlanadi, ixtiyoriy maydonga null yuborilsa tozalanadi. */
@Getter
public class WarehouseUpdateRequest {
    @Size(max = 160)
    private String name;

    @Positive
    private Long companyBranchId;

    @Positive
    private Long regionId;

    @Size(max = 1000)
    private String address;

    @JsonIgnore
    private boolean nameProvided;
    @JsonIgnore
    private boolean companyBranchIdProvided;
    @JsonIgnore
    private boolean regionIdProvided;
    @JsonIgnore
    private boolean addressProvided;

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    @JsonSetter("companyBranchId")
    public void setCompanyBranchId(Long companyBranchId) {
        this.companyBranchId = companyBranchId;
        this.companyBranchIdProvided = true;
    }

    @JsonSetter("regionId")
    public void setRegionId(Long regionId) {
        this.regionId = regionId;
        this.regionIdProvided = true;
    }

    @JsonSetter("address")
    public void setAddress(String address) {
        this.address = address;
        this.addressProvided = true;
    }

    public boolean hasChanges() {
        return nameProvided || companyBranchIdProvided || regionIdProvided || addressProvided;
    }
}
