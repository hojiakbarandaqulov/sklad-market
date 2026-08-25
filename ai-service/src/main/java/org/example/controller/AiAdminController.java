package org.example.controller;

import org.example.ai.embedding.IndexStatus;
import org.example.ai.embedding.ProductIndexer;
import org.example.dto.ApiResponse;
import org.example.dto.ReindexTriggerResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only operational endpoints for the embedding indexer (PLAN.md Phase 5). Dual-gated: the
 * gateway routes {@code /api/v1/ai/**} to this service where SecurityConfig already requires a valid
 * JWT, and {@code @PreAuthorize} additionally requires {@code ROLE_ADMIN}/{@code ROLE_SUPER_ADMIN}
 * (401 unauthenticated / 403 for a non-admin / 200 for an admin — the test matrix).
 */
@RestController
@RequestMapping("/api/v1/ai/admin")
public class AiAdminController {

    private final ProductIndexer indexer;

    public AiAdminController(ProductIndexer indexer) {
        this.indexer = indexer;
    }

    @PostMapping("/reindex")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<ReindexTriggerResponse> reindex() {
        boolean started = indexer.triggerAsyncReindex();
        return ApiResponse.successResponse(new ReindexTriggerResponse(
                started, started ? "Reindex started" : "A reindex is already in progress"));
    }

    @GetMapping("/reindex/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ApiResponse<IndexStatus> status() {
        return ApiResponse.successResponse(indexer.status());
    }
}
