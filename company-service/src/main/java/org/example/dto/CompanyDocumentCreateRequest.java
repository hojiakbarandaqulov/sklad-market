package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CompanyDocumentCreateRequest {
    @NotBlank(message = "documentType required")
    private String documentType;
    @NotBlank(message = "attachId required")
    private String attachId;
    @NotBlank(message = "fileUrl required")
    private String fileUrl;
}
