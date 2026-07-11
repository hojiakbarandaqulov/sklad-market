package org.example.exp.handle;

import org.example.exp.AppBadException;
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

@RestControllerAdvice
public class CustomGlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", "Validation failed");
        body.put("errors", errors);
        body.put("trace_id", UUID.randomUUID().toString());
        return new ResponseEntity<>(body, headers, status);
    }

    @ExceptionHandler(AppBadException.class)
    public ResponseEntity<Object> handleAppBadException(AppBadException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", exception.getMessage());
        body.put("trace_id", UUID.randomUUID().toString());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<Object> handleAccessDenied(Exception exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", exception.getMessage());
        body.put("trace_id", UUID.randomUUID().toString());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", exception.getMessage());
        body.put("trace_id", UUID.randomUUID().toString());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception exception) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", exception.getMessage());
        body.put("trace_id", UUID.randomUUID().toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
