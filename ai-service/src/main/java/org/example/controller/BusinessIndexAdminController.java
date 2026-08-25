package org.example.controller;

import org.example.ai.business.index.BusinessIndexStatus;
import org.example.ai.business.index.BusinessIndexer;
import org.example.dto.ApiResponse;
import org.example.dto.ReindexTriggerResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/admin/business-reindex")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class BusinessIndexAdminController {

    private final BusinessIndexer indexer;

    public BusinessIndexAdminController(BusinessIndexer indexer) {
        this.indexer = indexer;
    }

    @PostMapping
    public ApiResponse<ReindexTriggerResponse> reindex() {
        boolean started = indexer.triggerAsyncReindex();
        return ApiResponse.successResponse(new ReindexTriggerResponse(
                started, started ? "Business reindex started"
                        : "Business reindex is already running or could not be scheduled"));
    }

    @GetMapping("/status")
    public ApiResponse<BusinessIndexStatus> status() {
        return ApiResponse.successResponse(indexer.status());
    }
}
