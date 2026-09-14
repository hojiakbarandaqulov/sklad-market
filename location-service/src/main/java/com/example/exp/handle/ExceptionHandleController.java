package com.example.exp.handle;

import com.example.dto.ApiResponse;
import com.example.exp.AppBadException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExceptionHandleController {

    @ExceptionHandler(AppBadException.class)
    public ResponseEntity<ApiResponse<String>> handleBadRequest(AppBadException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(exception.getMessage(), Boolean.FALSE));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<String>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiResponse<>(exception.getMessage(), Boolean.FALSE));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<String>> handleAuthentication(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(exception.getMessage(), Boolean.FALSE));
    }
}
