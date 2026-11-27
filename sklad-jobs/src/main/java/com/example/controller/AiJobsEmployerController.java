package com.example.controller;

import com.example.dto.ApiResponse;
import com.example.dto.resume.AiJobsRequests;
import com.example.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/vacancies/ai")
@PreAuthorize("hasAnyRole('BUYER','SELLER')")
public class AiJobsEmployerController {
    private final AiCandidateDiscoveryService discovery;
    private final JobInvitationService invitations;
    @ModelAttribute
    void privateResponses(jakarta.servlet.http.HttpServletResponse response) {response.setHeader("Cache-Control","private, no-store");}
    @GetMapping("/owned")
    public ApiResponse<Map<String,Object>> owned() {return ApiResponse.successResponse(discovery.ownedVacancies());}
    @GetMapping("/candidates")
    public ApiResponse<AiCandidateDiscoveryService.CandidatePage> candidates(@RequestParam Long vacancyId,
            @RequestParam(required=false) String q,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="5") int perPage) {
        return ApiResponse.successResponse(discovery.candidates(vacancyId,q,page,perPage));
    }
    @GetMapping("/candidates/{resumeId}")
    public ApiResponse<AiCandidateDiscoveryService.CandidateDetail> candidate(@PathVariable Long resumeId,@RequestParam Long vacancyId) {
        return ApiResponse.successResponse(discovery.candidate(vacancyId,resumeId));
    }
    @PostMapping("/invitations")
    public ApiResponse<JobInvitationService.Invitation> invite(@RequestBody @Valid AiJobsRequests.Invitation request) {
        return ApiResponse.successResponse(invitations.send(request));
    }
    @GetMapping("/invitations")
    public ApiResponse<JobInvitationService.InvitationPage> invitations(@RequestParam(defaultValue="incoming") String direction,
            @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="10") int perPage) {
        return ApiResponse.successResponse(invitations.list(direction,page,perPage));
    }
    @PostMapping("/invitations/{id}/decline")
    public ApiResponse<JobInvitationService.Invitation> decline(@PathVariable Long id) {return ApiResponse.successResponse(invitations.decline(id));}
    @PostMapping("/invitations/{id}/withdraw")
    public ApiResponse<JobInvitationService.Invitation> withdraw(@PathVariable Long id) {return ApiResponse.successResponse(invitations.withdraw(id));}
}
