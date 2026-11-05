package org.example.dto.customer;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerCreateRequest {
    @NotNull @Positive
    private Long companyId;

    @NotBlank @Size(max = 255)
    private String name;

    @NotBlank @Size(max = 100)
    private String contractNumber;

    @Size(max = 30)
    @Pattern(regexp = "^$|^\\+?[0-9 ()-]{7,30}$", message = "Telefon raqami noto'g'ri")
    private String phone;

    @NotNull @Min(1) @Max(3650)
    private Integer paymentTermDays;
}
