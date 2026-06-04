package org.example.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private Boolean success;
    private T data;
    private String message;


    private ApiResponse(Boolean success) {
        this.success = success;
    }

    private ApiResponse(T data, Boolean success) {
        this.data = data;
        this.success = success;
    }

    private ApiResponse(T data, Boolean success, String message) {
        this.data = data;
        this.success = success;
        this.message = message;
    }

    private ApiResponse(String message, Boolean success) {
        this.message = message;
        this.success = success;
    }

    public ApiResponse(T data) {
        this.data = data;
    }


    public static <E> ApiResponse<E> successResponse(E data) {
        return new ApiResponse<>(data, Boolean.TRUE);
    }

    public static <E> ApiResponse<E> successResponse(E data, String message) {
        return new ApiResponse<>(data, Boolean.TRUE, message);
    }

    public static <E> ApiResponse<E> successResponse() {
        return new ApiResponse<>(Boolean.TRUE);
    }

    public static ApiResponse<String> successResponse(String message) {
        return new ApiResponse<>(message, Boolean.TRUE);
    }

}
