package org.example.dto.report;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReportBlockRequest {
    @NotNull(message = "reason required")
    private String reason;
}
