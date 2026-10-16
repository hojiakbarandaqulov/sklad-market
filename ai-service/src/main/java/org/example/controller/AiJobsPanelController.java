package org.example.controller;

import org.example.ai.jobs.*;
import org.example.ai.guardrail.RpmRateLimiter;
import org.example.ai.error.*;
import org.example.dto.ApiResponse;
import org.example.security.AiSecurityUtil;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/ai/jobs")
public class AiJobsPanelController {
    private final JobsPanelService panel;
    private final JobMatchingService matching;
    private final RpmRateLimiter rpm;
    public AiJobsPanelController(JobsPanelService panel, JobMatchingService matching, RpmRateLimiter rpm) {
        this.panel = panel; this.matching = matching; this.rpm = rpm;
    }
    @PostMapping("/panel")
    public ResponseEntity<?> panel(@RequestBody JobsPanelService.Request request,
            @RequestHeader(value = "Accept-Language", required = false) String language) {
        String owner = AiSecurityUtil.requireSub(); guard(owner);
        return response(panel.search(request, owner, AiSecurityUtil.currentRoleSet(), AiSecurityUtil.requireBearerToken(), language));
    }
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String query, @RequestParam(defaultValue = "5") int limit,
            @RequestParam(required = false) Long regionId, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) java.math.BigDecimal salaryMin,
            @RequestParam(required = false) String experienceLevel, @RequestParam(required = false) String employmentType,
            @RequestParam(required = false) String workSchedule,
            @RequestHeader(value = "Accept-Language", required = false) String language) {
        guard(AiSecurityUtil.requireSub());
        return response(matching.search(new JobCriteria(query, regionId, categoryId, salaryMin, experienceLevel, employmentType, workSchedule, limit).validated(true), language));
    }
    private void guard(String owner) {
        if (!rpm.tryConsume("jobs-panel:" + owner, 20)) throw new AiChatException(AiErrorCode.RATE_LIMITED, "Too many job searches; try again shortly.");
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<?> invalidRequest(Exception ignored) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(ApiResponse.errorResponse("Invalid Jobs search request"));
    }
    private static ResponseEntity<?> response(Object data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.successResponse(data)); }
}
