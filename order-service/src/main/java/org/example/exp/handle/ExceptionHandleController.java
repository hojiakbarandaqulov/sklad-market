package org.example.exp.handle;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.example.exp.OrderException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class ExceptionHandleController extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception) {
        return buildErrorResponse(400, "So'rov maydonlari noto'g'ri", Map.of());
    }

    @ExceptionHandler(OrderException.class)
    public ResponseEntity<Object> handleOrderException(OrderException exception) {
        String traceId = UUID.randomUUID().toString();
        if (exception.getStatus() >= 500) {
            // Token, header va bog'liq servisning javob matnini logga yozmaymiz.
            log.warn("Order so'rovi bajarilmadi, trace_id={}, status={}, diagnostic={}",
                    traceId, exception.getStatus(), exception.getDiagnostic());
        }
        return buildErrorResponse(exception.getStatus(), exception.getMessage(), Map.of(), traceId);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException exception) {
        return buildErrorResponse(403, "Bu amalga ruxsat yo'q", Map.of());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, CannotAcquireLockException.class})
    public ResponseEntity<Object> handleDataConflict(RuntimeException exception) {
        return buildErrorResponse(
                409,
                "So'rov to'qnashuvi. Avvalgi kalit bilan qayta yuboring",
                Map.of()
        );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage()));

        return buildErrorResponse(400, "So'rov maydonlari noto'g'ri", fieldErrors);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleUnexpectedException(RuntimeException exception) {
        String traceId = UUID.randomUUID().toString();
        log.error("Order xatosi, trace_id={}", traceId, exception);

        Map<String, Object> body = Map.of(
                "success", false,
                "message", "Ichki xatolik",
                "trace_id", traceId
        );

        return ResponseEntity.internalServerError().body(body);
    }

    private ResponseEntity<Object> buildErrorResponse(
            int status,
            String message,
            Map<String, String> fieldErrors
    ) {
        return buildErrorResponse(status, message, fieldErrors, UUID.randomUUID().toString());
    }

    private ResponseEntity<Object> buildErrorResponse(
            int status,
            String message,
            Map<String, String> fieldErrors,
            String traceId
    ) {
        // trace_id orqali frontenddagi xabarni server logidagi xatolik bilan topish mumkin.
        Map<String, Object> body = Map.of(
                "success", false,
                "message", message,
                "errors", fieldErrors,
                "trace_id", traceId
        );

        return ResponseEntity.status(status).body(body);
    }
}
