package org.example.controller;

import org.example.ai.jobs.*;
import org.example.ai.guardrail.RpmRateLimiter;
import org.example.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiJobsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "server.domain=http://localhost")
class AiJobsControllerTest {
    @Autowired MockMvc mvc;
    @MockBean JwtDecoder decoder;
    @MockBean JobPreferencesStore profiles;
    @MockBean JobMatchingService matching;
    @MockBean PublicJobsClient jobs;
    @MockBean RpmRateLimiter limiter;
    @MockBean ResumeJobMatchingService resumeMatching;

    @Test void guestsCannotReadOrWritePreferencesRecommendationsOrCategories() throws Exception {
        for (String endpoint : List.of("preferences", "recommendations", "categories"))
            mvc.perform(get("/api/v1/ai/jobs/" + endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/ai/jobs/preferences")).andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles, jobs, matching);
    }
    @Test void profileOwnershipAlwaysComesFromJwtNotQueryParameter() throws Exception {
        when(profiles.find("actual-owner")).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/ai/jobs/preferences?userSub=someone-else")
                .with(jwt().jwt(j -> j.subject("actual-owner"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(profiles).find("actual-owner");
        mvc.perform(delete("/api/v1/ai/jobs/preferences?userSub=someone-else")
                .with(jwt().jwt(j -> j.subject("actual-owner"))))
                .andExpect(status().isNoContent());
        verify(profiles).delete("actual-owner");
    }
    @Test void allRegisteredRolesCanOptInAndRecommendationReadsUseOnlyDeterministicMatching() throws Exception {
        when(limiter.tryConsume(anyString(), eq(20))).thenReturn(true);
        when(matching.recommend(eq("owner"), any(), any())).thenReturn(JobMatchingService.profileRequired());
        for (String role : List.of("BUYER", "SELLER", "ADMIN", "SUPER_ADMIN", "PREMIUM"))
            mvc.perform(get("/api/v1/ai/jobs/recommendations").with(jwt().jwt(j -> j.subject("owner"))
                    .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.kind").value("job_profile_required"));
        verifyNoInteractions(profiles, jobs);
    }
    @Test void boundedRestRateGuardRejectsBeforeDownstreamJobs() throws Exception {
        when(limiter.tryConsume(anyString(), eq(20))).thenReturn(false);
        mvc.perform(get("/api/v1/ai/jobs/recommendations").with(jwt()))
                .andExpect(status().isTooManyRequests());
        verifyNoInteractions(matching, jobs);
    }
    @Test void invalidPreferencesCannotReachPersistenceOrPlatform() throws Exception {
        when(limiter.tryConsume(anyString(), eq(20))).thenReturn(true);
        mvc.perform(put("/api/v1/ai/jobs/preferences").with(jwt()).contentType("application/json")
                .content("{\"desiredPosition\":\"Driver\",\"skills\":[\"\"],\"regionId\":1,\"experienceLevel\":\"NO_EXPERIENCE\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(profiles, jobs);
    }
    @Test void emptyMalformedAndNonNumericInputsReturn400NotServerErrors() throws Exception {
        mvc.perform(put("/api/v1/ai/jobs/preferences").with(jwt()).contentType("application/json").content("null"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/ai/jobs/preferences").with(jwt()).contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/ai/jobs/recommendations?limit=invalid").with(jwt())).andExpect(status().isBadRequest());
        verifyNoInteractions(profiles, jobs, matching);
    }
}
