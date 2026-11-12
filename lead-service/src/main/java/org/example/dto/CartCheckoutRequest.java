package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class CartCheckoutRequest {
    @NotBlank
    private String contactName;
    @NotBlank
    private String contactPhone;
    private List<Long> cartItemIds;
    private String contactEmail;
    private String deliveryAddress;
    private LocalDate neededDate;
    private String comment;
}
