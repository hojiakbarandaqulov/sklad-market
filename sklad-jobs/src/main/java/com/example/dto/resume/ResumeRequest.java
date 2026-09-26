package com.example.dto.resume;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class ResumeRequest {
    @NotBlank @Size(max = 255)
    private String title;
    @NotBlank @Size(max = 255)
    private String fullName;
    @NotBlank @Size(max = 32)
    @Pattern(regexp = "\\+?[0-9][0-9 ()-]{6,30}")
    private String phone;
    @NotBlank @Email @Size(max = 255)
    private String email;
    @NotNull @Positive
    private Long regionId;
    @Size(max = 255)
    private String desiredPosition;
    @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
    private BigDecimal expectedSalary;
    @Size(max = 10000)
    private String aboutMe;
    @Size(max = 10000)
    private String skills;
    @Size(max = 20000)
    private String workExperience;
    @Size(max = 20000)
    private String education;
    @Size(max = 10000)
    private String languages;
}