package com.example.dto.application;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class ApplicationCreateRequest {
    @NotNull
    private Long resumeId;
    @NotBlank @Size(max = 255)
    private String fullName;
    @NotBlank @Size(max = 32)
    @Pattern(regexp = "\\+?[0-9][0-9 ()-]{6,30}")
    private String phone;
    @NotBlank @Email @Size(max = 255)
    private String email;
    @NotNull
    private Long regionId;
    @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
    private BigDecimal expectedSalary;
    @Size(max = 10000)
    private String coverLetter;
    @NotNull @AssertTrue
    private Boolean consentAccepted;
}