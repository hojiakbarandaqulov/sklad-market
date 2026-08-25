package org.example.dto.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatSellerCreate {
    @NotBlank(message = "subject required")
    private String subject;
}
