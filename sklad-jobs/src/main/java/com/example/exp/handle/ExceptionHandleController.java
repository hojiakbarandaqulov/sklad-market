package com.example.exp.handle;

import com.example.exp.AppBadException;
import com.example.service.ResourceBundleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
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

@RequiredArgsConstructor
@RestControllerAdvice
public class ExceptionHandleController extends ResponseEntityExceptionHandler {
    private final ResourceBundleService messageService;

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        return new ResponseEntity<>(errorBody(messageService.getMessage("validation.failed"), errors), headers, status);
    }

    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handle(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(errorBody(e.getMessage(), Map.of()));
    }

    @ExceptionHandler(AppBadException.class)
    public ResponseEntity<Map<String, Object>> handleException(AppBadException e) {
        return ResponseEntity.badRequest().body(errorBody(e.getMessage(), Map.of()));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<Map<String, Object>> handleAccessDenied(Exception e) {
        String message = e instanceof AuthorizationDeniedException
                ? messageService.getMessage("auth.access.denied")
                : e.getMessage();
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(errorBody(message, Map.of()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(errorBody(messageService.getMessage("auth.unauthorized"), Map.of()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handle(RuntimeException e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(messageService.getMessage("error.internal"), Map.of()));
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
