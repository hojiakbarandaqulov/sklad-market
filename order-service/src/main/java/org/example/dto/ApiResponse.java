package org.example.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private Boolean success;
    private T data;
    private String message;

    private ApiResponse(T data, Boolean success) {
        this.data = data;
        this.success = success;
    }

    public static <T> ApiResponse<T> successResponse(T data) {
        return new ApiResponse<>(data, Boolean.TRUE);
    }
}
