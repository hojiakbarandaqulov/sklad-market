package org.example.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistrationVerificationDTO {

    @NotBlank(message = "username required")
    private String username;

    @NotBlank(message = "confirmPassword required")
    private String confirmPassword;
}
