package com.example.controller;

import com.example.dto.resume.ResumeDTO;
import com.example.enums.AppLanguage;
import com.example.exp.handle.ExceptionHandleController;
import com.example.service.JobApplicationService;
import com.example.service.ResourceBundleService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApplicationResumeControllerTest {
    @Configuration
    @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean JobApplicationService service() { return mock(JobApplicationService.class); }
        @Bean JobApplicationController controller(JobApplicationService service) {
            return new JobApplicationController(service);
        }
    }

    private AnnotationConfigApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        ResourceBundleService messages = mock(ResourceBundleService.class);
        when(messages.getMessage(anyString())).thenAnswer(call -> call.getArgument(0));
        mvc = MockMvcBuilders.standaloneSetup(context.getBean(JobApplicationController.class))
                .setControllerAdvice(new ExceptionHandleController(messages)).build();
    }

    @AfterEach
    void tearDown() { SecurityContextHolder.clearContext(); context.close(); }

    @Test
    void buyerAndSellerCanRequestApplicationResumeWithLanguageHeader() throws Exception {
        ResumeDTO resume = new ResumeDTO();
        resume.setId(501L);
        resume.setTitle("Submitted resume");
        when(context.getBean(JobApplicationService.class).getApplicationResume(9001L, AppLanguage.UZ))
                .thenReturn(resume);
        for (String role : List.of("BUYER", "SELLER")) {
            authenticate(role);
            mvc.perform(get("/api/v1/me/applications/9001/resume").header("Accept-Language", "UZ"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(501))
                    .andExpect(jsonPath("$.data.title").value("Submitted resume"));
        }
        verify(context.getBean(JobApplicationService.class), times(2)).getApplicationResume(9001L, AppLanguage.UZ);
    }

    @Test
    void otherRolesCannotRequestPrivateApplicationResume() throws Exception {
        authenticate("ADMIN");
        mvc.perform(get("/api/v1/me/applications/9001/resume")).andExpect(status().isForbidden());
        verifyNoInteractions(context.getBean(JobApplicationService.class));
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/me/applications/9001/resume")).andExpect(status().isUnauthorized());
        verifyNoInteractions(context.getBean(JobApplicationService.class));
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test", "", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
