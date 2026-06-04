package org.example.dto.report;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportWarnRequest {
    @NotNull(message = "message required")
    private String message;
}
