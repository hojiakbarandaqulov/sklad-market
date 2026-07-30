package org.example.dto.support;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupportCreateRequest {
    @Size(max = 300, message = "{support.subject.size}")
    private String subject;
}
