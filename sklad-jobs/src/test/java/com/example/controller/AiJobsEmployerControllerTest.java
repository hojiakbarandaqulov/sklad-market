package com.example.controller;

import com.example.dto.resume.AiJobsRequests;
import com.example.exp.handle.ExceptionHandleController;
import com.example.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiJobsEmployerControllerTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean AiCandidateDiscoveryService discovery() {return mock(AiCandidateDiscoveryService.class);}
        @Bean JobInvitationService invitations() {return mock(JobInvitationService.class);}
        @Bean AiJobsEmployerController employer(AiCandidateDiscoveryService d,JobInvitationService i) {return new AiJobsEmployerController(d,i);}
        @Bean AiResumeDiscoveryController resume(AiCandidateDiscoveryService d) {return new AiResumeDiscoveryController(d);}
    }
    AnnotationConfigApplicationContext context;
    MockMvc mvc;
    AiCandidateDiscoveryService discovery;
    JobInvitationService invitations;
    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext(Config.class);
        discovery=context.getBean(AiCandidateDiscoveryService.class);invitations=context.getBean(JobInvitationService.class);
        ResourceBundleService messages=mock(ResourceBundleService.class);when(messages.getMessage(anyString())).thenAnswer(i->i.getArgument(0));
        mvc=MockMvcBuilders.standaloneSetup(context.getBean(AiJobsEmployerController.class),context.getBean(AiResumeDiscoveryController.class))
                .setControllerAdvice(new ExceptionHandleController(messages)).build();
    }
    @AfterEach void cleanup() {SecurityContextHolder.clearContext();context.close();}
    void role(String role) {SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("caller","",List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
    @Test void guestsCannotReadOrSearchOrModifyDiscovery() throws Exception {
        mvc.perform(get("/api/v1/vacancies/ai/candidates").param("vacancyId","42")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/resumes/501/matching-profile")).andExpect(status().isUnauthorized());
        verifyNoInteractions(discovery,invitations);
    }
    @Test void candidateResponseIsPrivateAndUsesStandardEnvelope() throws Exception {
        role("SELLER");
        when(discovery.candidates(42L,null,1,5)).thenReturn(new AiCandidateDiscoveryService.CandidatePage(List.of(),1,5,false,42L));
        mvc.perform(get("/api/v1/vacancies/ai/candidates").param("vacancyId","42"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.perPage").value(5));
    }
    @Test void malformedInvitationFailsBeforeWritesAndWithdrawalNeedsNoRepeatedFields() throws Exception {
        role("BUYER");
        mvc.perform(post("/api/v1/vacancies/ai/invitations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumeId\":501,\"vacancyId\":42}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(invitations);
        when(discovery.saveDiscovery(eq(501L),any())).thenReturn(new AiCandidateDiscoveryService.Discovery(false,null,null,null,List.of()));
        mvc.perform(put("/api/v1/me/resumes/501/discovery").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
    }
    @Test void adminLabelAloneDoesNotConferEmployerPrivileges() throws Exception {
        role("ADMIN");
        mvc.perform(get("/api/v1/vacancies/ai/owned")).andExpect(status().isForbidden());
        verifyNoInteractions(discovery,invitations);
    }
}
