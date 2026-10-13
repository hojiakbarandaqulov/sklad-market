package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.vacancy.VacancyFilter;
import com.example.service.AiVacancyCandidatesService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Read-only AI adapter; ordinary Jobs search/detail/application routes remain unchanged. */
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/vacancies/ai-candidates")
public class AiVacancyCandidatesController {
    private final AiVacancyCandidatesService service;

    @GetMapping
    public ApiResponse<?> candidates(@Valid @ModelAttribute VacancyFilter filter,
            @RequestParam(required = false) @Size(max = 12) List<@Size(min = 2, max = 60) String> terms,
            @RequestParam(required = false) @Size(max = 6) List<@Positive Long> ids,
            @RequestParam(defaultValue = "40") @Min(1) @Max(40) int limit) {
        if (limit < 1 || limit > 40 || terms != null && (terms.size() > 12 || terms.stream().anyMatch(t -> t == null || t.length() < 2 || t.length() > 60))
                || ids != null && (ids.size() > 6 || ids.stream().anyMatch(id -> id == null || id <= 0)))
            throw new IllegalArgumentException("Invalid AI candidate bounds");
        return ApiResponse.successResponse(service.find(filter, terms, ids, limit));
    }

    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public org.springframework.http.ResponseEntity<?> invalidRequest(Exception ignored) {
        return org.springframework.http.ResponseEntity.badRequest().body(java.util.Map.of("success", false, "message", "Invalid AI candidate parameters"));
    }
}
