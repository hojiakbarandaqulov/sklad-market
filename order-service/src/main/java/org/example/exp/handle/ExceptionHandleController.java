package org.example.exp.handle;

import org.springframework.http.*;
import org.example.exp.OrderException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.CannotAcquireLockException;
import lombok.extern.slf4j.Slf4j;
import java.util.*;

@RestControllerAdvice @Slf4j
public class ExceptionHandleController extends ResponseEntityExceptionHandler {
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    ResponseEntity<Object> validation(jakarta.validation.ConstraintViolationException e) {
        return error(400, "So'rov maydonlari noto'g'ri", Map.of());
    }
    @ExceptionHandler(OrderException.class)
    ResponseEntity<Object> business(OrderException e) { return error(e.getStatus(), e.getMessage(), Map.of()); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> forbidden(AccessDeniedException e) { return error(403, "Bu amalga ruxsat yo'q", Map.of()); }
    @ExceptionHandler({DataIntegrityViolationException.class, CannotAcquireLockException.class})
    ResponseEntity<Object> conflict(RuntimeException e) { return error(409, "So'rov to'qnashuvi. Avvalgi kalit bilan qayta yuboring", Map.of()); }
    @Override protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var fields = new LinkedHashMap<String, String>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return error(400, "So'rov maydonlari noto'g'ri", fields);
    }
    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<Object> unexpected(RuntimeException e) {
        String trace = UUID.randomUUID().toString();
        log.error("Order xatosi, trace_id={}", trace, e);
        return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Ichki xatolik", "trace_id", trace));
    }
    private ResponseEntity<Object> error(int status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(Map.of("success", false, "message", message, "errors", fields, "trace_id", UUID.randomUUID().toString()));
    }
}
