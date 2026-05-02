package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CompanyDocumentResponse {
    private Long id;
    private Long companyId;
    private String documentType;
    private String attachId;
    private String fileUrl;
    private String status;
    private LocalDateTime createdAt;
}
