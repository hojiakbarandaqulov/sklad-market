package org.example.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SuggestedAttributeDto {
    private String code;
    private String label;
    private String dataType;
    private Object value;
}
