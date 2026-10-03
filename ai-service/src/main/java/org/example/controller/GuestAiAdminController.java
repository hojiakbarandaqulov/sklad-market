package org.example.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.example.ai.guest.GuestTrialStore;
import org.example.dto.ApiResponse;
import org.example.security.AiSecurityUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/admin/guest-trial")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
public class GuestAiAdminController {
    private final GuestTrialStore store;
    public GuestAiAdminController(GuestTrialStore store) { this.store = store; }
    public record PolicyRequest(@NotNull @Min(0) @Max(20) Integer messageLimit) {}
    @GetMapping
    public ApiResponse<GuestTrialStore.Policy> get() { return ApiResponse.successResponse(store.policy()); }
    @PutMapping
    public ApiResponse<GuestTrialStore.Policy> update(@Valid @RequestBody PolicyRequest body) {
        return ApiResponse.successResponse(store.update(body.messageLimit(), AiSecurityUtil.requireSub()));
    }
}
