package org.example.controller;

import org.example.ai.jobs.*;
import org.example.ai.guardrail.RpmRateLimiter;
import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.dto.ApiResponse;
import org.example.security.AiSecurityUtil;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** These deterministic owner-scoped endpoints do not consume LLM quota or create chats. */
@RestController
@RequestMapping("/api/v1/ai/jobs")
public class AiJobsController {
    private final JobPreferencesStore profiles;
    private final JobMatchingService matching;
    private final PublicJobsClient jobs;
    private final RpmRateLimiter limiter;
    public AiJobsController(JobPreferencesStore profiles, JobMatchingService matching, PublicJobsClient jobs, RpmRateLimiter limiter) {
        this.profiles = profiles; this.matching = matching; this.jobs = jobs; this.limiter = limiter;
    }
    public record ProfileResponse(JobPreferences preferences) {}
    @GetMapping("/preferences")
    public ResponseEntity<?> get() { return response(new ProfileResponse(profiles.find(AiSecurityUtil.requireSub()).orElse(null))); }
    @PutMapping("/preferences")
    public ResponseEntity<?> put(@RequestBody(required = false) JobPreferences preferences,
            @RequestHeader(value = "Accept-Language", required = false) String language) {
        String owner = AiSecurityUtil.requireSub();
        if (preferences == null) throw new IllegalArgumentException("Job preferences are required");
        guard("save", owner);
        JobPreferences safe = preferences.validated(); jobs.validateCategory(safe.categoryId(), language);
        jobs.validateRegion(safe.regionId(), language);
        return response(new ProfileResponse(profiles.save(owner, safe)));
    }
    @DeleteMapping("/preferences")
    public ResponseEntity<?> delete() { profiles.delete(AiSecurityUtil.requireSub()); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build(); }
    @GetMapping("/categories")
    public ResponseEntity<?> categories(@RequestHeader(value = "Accept-Language", required = false) String language) {
        guard("categories", AiSecurityUtil.requireSub()); return response(Map.of("items", jobs.categories(language)));
    }
    @GetMapping("/recommendations")
    public ResponseEntity<?> recommendations(@RequestParam(defaultValue = "5") int limit,
            @RequestHeader(value = "Accept-Language", required = false) String language) {
        String owner = AiSecurityUtil.requireSub();
        JobCriteria criteria = JobCriteria.defaults(limit).validated(false);
        guard("recommendations", owner);
        return response(matching.recommend(owner, criteria, language));
    }
    private static ResponseEntity<?> response(Object data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.successResponse(data)); }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalidRequest(Exception ignored) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(ApiResponse.errorResponse("Invalid job preferences or request parameters"));
    }
    private void guard(String operation, String owner) {
        if (!limiter.tryConsume("jobs-" + operation + ":" + owner, 20))
            throw new AiChatException(AiErrorCode.RATE_LIMITED, "Too many job requests; please try again shortly.");
    }
}
