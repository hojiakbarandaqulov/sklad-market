package com.example.exp.handle;

import com.example.exp.AppBadException;
import com.example.exp.AppConflictException;
import com.example.exp.AppNotFoundException;
import com.example.exp.AppServiceUnavailableException;
import com.example.service.ResourceBundleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ExceptionHandleController extends ResponseEntityExceptionHandler {
    private final ResourceBundleService messageService;

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return new ResponseEntity<>(errorBody(messageService.getMessage("validation.failed"), errors), headers, status);
    }

    @ExceptionHandler({AppBadException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException exception) {
        return ResponseEntity.badRequest().body(errorBody(exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(AppNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(AppNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(AppConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(AppConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrityViolation(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody(messageService.getMessage("data.conflict"), Map.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorBody(messageService.getMessage("auth.access.denied"), Map.of()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorBody(messageService.getMessage("auth.unauthorized"), Map.of()));
    }

    @ExceptionHandler(AppServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleServiceUnavailable(AppServiceUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorBody(exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(RuntimeException exception) {
        Map<String, Object> body = errorBody(messageService.getMessage("error.internal"), Map.of());
        log.error("Unexpected Sklad Control error, trace_id={}", body.get("trace_id"), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private Map<String, Object> errorBody(String message, Map<String, ?> errors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", message);
        body.put("errors", errors);
        body.put("trace_id", UUID.randomUUID().toString());
        return body;
    }
}
