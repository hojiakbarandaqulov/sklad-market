package org.example.exp.handle;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.example.service.ResourceBundleService;
import org.example.enums.AppLanguage;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.example.controller.CustomerController;
import org.example.exp.CustomerException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

@Slf4j
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = CustomerController.class)
public class CustomerExceptionHandler extends ResponseEntityExceptionHandler {
    private final ResourceBundleService messages;

    @ExceptionHandler(CustomerException.class)
    ResponseEntity<Object> customerError(CustomerException ex) {
        return errorResponse(ex.getStatus(), ex.getMessage(), Map.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> constraintError(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(),
                message("customer.validation." + v.getConstraintDescriptor().getAnnotation()
                        .annotationType().getSimpleName().toLowerCase(Locale.ROOT))));
        return error(400, "customer.validation.failed", errors);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> forbidden(AccessDeniedException ex) {
        return error(403, "customer.forbidden", Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> unauthorized(AuthenticationException ex) {
        return error(401, "customer.unauthorized", Map.of());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<Object> conflict(RuntimeException ex) {
        return error(409, "customer.conflict", Map.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(),
                message("customer.validation." + e.getCode().toLowerCase(Locale.ROOT))));
        return error(400, "customer.validation.failed", errors);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return error(413, "customer.file.too.large", Map.of());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var response = error(status.value(), "customer.request.invalid", Map.of());
        return new ResponseEntity<>(response.getBody(), headers, status);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<Object> unexpected(RuntimeException ex) {
        String traceId = UUID.randomUUID().toString();
        log.error("Customer API error, trace_id={}", traceId, ex);
        return ResponseEntity.internalServerError().body(Map.of(
                "success", false, "message", message("customer.internal.error"), "trace_id", traceId));
    }

    private ResponseEntity<Object> error(int status, String message, Map<String, String> errors) {
        return errorResponse(status, message(message), errors);
    }

    private ResponseEntity<Object> errorResponse(int status, String message, Map<String, String> errors) {
        return ResponseEntity.status(status).body(Map.of("success", false, "message", message,
                "errors", errors, "trace_id", UUID.randomUUID().toString()));
    }

    private String message(String code) {
        AppLanguage language = AppLanguage.UZ;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            var request = attributes.getRequest();
            if (request.getHeader(HttpHeaders.ACCEPT_LANGUAGE) != null) {
                var locales = request.getLocales();
                while (locales.hasMoreElements()) {
                    String candidate = locales.nextElement().getLanguage().toUpperCase(Locale.ROOT);
                    try {
                        language = AppLanguage.valueOf(candidate);
                        break;
                    } catch (IllegalArgumentException ignored) {
                        // Try the next language preference, then fall back to Uzbek.
                    }
                }
            }
        }
        // Binding errors (for example, a non-numeric form field) also need translation.
        if (code.startsWith("customer.validation.") && !java.util.Set.of(
                "notnull", "notblank", "size", "pattern", "positive", "min", "max", "failed")
                .contains(code.substring("customer.validation.".length()))) {
            code = "customer.request.invalid";
        }
        return messages.getMessage(code, language);
    }
}
