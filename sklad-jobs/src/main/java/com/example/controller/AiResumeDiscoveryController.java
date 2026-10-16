package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.resume.AiJobsRequests;
import com.example.service.AiCandidateDiscoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/me/resumes")
@PreAuthorize("hasAnyRole('BUYER','SELLER')")
public class AiResumeDiscoveryController {
    private final AiCandidateDiscoveryService discovery;
    @ModelAttribute
    void privateResponses(jakarta.servlet.http.HttpServletResponse response) {response.setHeader("Cache-Control","private, no-store");}
    @GetMapping("/{id}/discovery")
    public ApiResponse<AiCandidateDiscoveryService.Discovery> get(@PathVariable Long id) {return ApiResponse.successResponse(discovery.discovery(id));}
    @PutMapping("/{id}/discovery")
    public ApiResponse<AiCandidateDiscoveryService.Discovery> save(@PathVariable Long id,@RequestBody @Valid AiJobsRequests.Discovery request) {
        return ApiResponse.successResponse(discovery.saveDiscovery(id,request));
    }
    @GetMapping("/{id}/matching-profile")
    public ApiResponse<AiCandidateDiscoveryService.MatchingProfile> matchingProfile(@PathVariable Long id) {
        return ApiResponse.successResponse(discovery.matchingProfile(id));
    }
}
