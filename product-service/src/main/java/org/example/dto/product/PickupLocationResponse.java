package org.example.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PickupLocationResponse {
    @JsonProperty("branch_id")
    private Long branchId;
    private String name;
    private String address;
    private String phone;
    private String lat;
    private String lng;
}
