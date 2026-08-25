package org.example.exception;

import lombok.extern.slf4j.Slf4j;
import org.example.ai.error.AiChatException;
import org.example.ai.gateway.GatewayUnavailableException;
import org.example.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Applies only to the plain JSON (non-SSE) endpoints. The streaming endpoint never lets an
 * exception reach here once its {@code SseEmitter} has been returned — turn failures are typed
 * {@code error} SSE events instead (PLAN.md §6).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AiNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(AiNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.errorResponse(e.getMessage()));
    }

    @ExceptionHandler(ActionDraftStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleDraftState(ActionDraftStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.errorResponse(e.getMessage()));
    }

    @ExceptionHandler(GatewayUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleGatewayUnavailable(GatewayUnavailableException e) {
        log.warn("Gateway unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.errorResponse("The platform is temporarily unavailable."));
    }

    /**
     * Provider/guardrail failures on the plain-JSON endpoints (semantic search embeds the query on
     * the request thread — no SSE to carry a typed error, so it maps to an HTTP status here).
     */
    @ExceptionHandler(AiChatException.class)
    public ResponseEntity<ApiResponse<Void>> handleAiChat(AiChatException e) {
        HttpStatus status = switch (e.code()) {
            case RATE_LIMITED, BUDGET_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case INVALID_INPUT -> HttpStatus.BAD_REQUEST;
            case PROVIDER_ERROR -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        if (status.is5xxServerError()) {
            log.warn("AI provider error on JSON endpoint (code={}): {}", e.code(), e.getMessage());
        }
        return ResponseEntity.status(status)
                .header("X-AI-Error-Code", e.code().wireCode())
                .body(ApiResponse.errorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(ApiResponse.errorResponse(e.getMessage()));
    }

    /**
     * {@code @PreAuthorize} denials (the admin endpoints — first role-gated routes in ai-service).
     * Without this, the catch-all below would turn Spring Security's {@code AuthorizationDeniedException}
     * (an {@link AccessDeniedException}) into a 500 for an authenticated-but-unauthorized caller
     * instead of the correct 403. Unauthenticated callers are already rejected with 401 at the filter.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.errorResponse("Access denied"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.errorResponse(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception in ai-service", e);
        return ResponseEntity.internalServerError().body(ApiResponse.errorResponse("Unexpected error"));
    }
}
