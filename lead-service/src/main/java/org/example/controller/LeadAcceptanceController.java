package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.*;
import org.example.enums.AppLanguage;
import org.example.service.impl.LeadAcceptanceService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/leads")
public class LeadAcceptanceController {
    private final LeadAcceptanceService service;
    @PostMapping("/{id}/accept") @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<JsonNode> accept(@PathVariable Long id, @Valid @RequestBody LeadAcceptRequest request,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value="Accept-Language",defaultValue="UZ") AppLanguage language) {
        return ApiResponse.successResponse(service.accept(id,request,authorization,language));
    }
}
