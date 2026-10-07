package com.example.controller;

import com.example.dto.resume.ResumeDTO;
import com.example.dto.resume.ResumeRequest;
import com.example.enums.AppLanguage;
import com.example.exp.AppNotFoundException;
import com.example.exp.handle.ExceptionHandleController;
import com.example.service.ResumeService;
import com.example.service.ResourceBundleService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ResumeControllerTest {
    private final ResumeService service = mock(ResumeService.class);
    private MockMvc mvc;
    private final String body = "{\"title\":\"Java\",\"fullName\":\"Ali\",\"phone\":\"+998901234567\",\"email\":\"ali@example.com\",\"regionId\":1}";
    @BeforeEach void setup() {
        ResourceBundleService messages = mock(ResourceBundleService.class);
        when(messages.getMessage(anyString())).thenAnswer(i -> i.getArgument(0));
        mvc = MockMvcBuilders.standaloneSetup(new ResumeController(service))
            .setControllerAdvice(new ExceptionHandleController(messages)).build();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void createReturns201AndSuccessWrapper() throws Exception {
        ResumeDTO dto = new ResumeDTO(); dto.setId(501L);
        when(service.createResume(any(),eq(AppLanguage.UZ))).thenReturn(dto);
        mvc.perform(post("/api/v1/me/resumes").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.id").value(501));
    }
    @Test void blankRequiredFieldFailsBeforeServiceCall() throws Exception {
        mvc.perform(post("/api/v1/me/resumes").contentType(MediaType.APPLICATION_JSON).content(body.replace("Java"," ")))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void invalidEmailPhoneAndSalaryFailValidation() throws Exception {
        String invalid = body.replace("ali@example.com","bad-email").replace("+998901234567","abc").replace("\"regionId\":1","\"regionId\":1,\"expectedSalary\":-1");
        mvc.perform(put("/api/v1/me/resumes/501").contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void notFoundReturns404() throws Exception {
        when(service.getResume(501L,AppLanguage.UZ)).thenThrow(new AppNotFoundException("Resume not found"));
        mvc.perform(get("/api/v1/me/resumes/501")).andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
    }
    @Test void deleteReturns204WithoutBody() throws Exception {
        mvc.perform(delete("/api/v1/me/resumes/501")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).deleteResume(501L,AppLanguage.UZ);
    }
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    static class MethodSecurityConfig {
        @Bean ResumeService resumeService() { return mock(ResumeService.class); }
        @Bean ResumeController resumeController(ResumeService service) { return new ResumeController(service); }
    }
    @Test void sellerCanReadResumesButCannotCreateUpdateOrDelete() {
        try (var context = new AnnotationConfigApplicationContext(MethodSecurityConfig.class)) {
            ResumeController controller = context.getBean(ResumeController.class);
            ResumeService protectedService = context.getBean(ResumeService.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("seller","",List.of(new SimpleGrantedAuthority("ROLE_SELLER"))));
            controller.getResume(501L,AppLanguage.UZ);
            assertThrows(AccessDeniedException.class, () -> controller.createResume(new ResumeRequest(),AppLanguage.UZ));
            assertThrows(AccessDeniedException.class, () -> controller.updateResume(501L,new ResumeRequest(),AppLanguage.UZ));
            assertThrows(AccessDeniedException.class, () -> controller.deleteResume(501L,AppLanguage.UZ));
            controller.getMyResumes(1,20,AppLanguage.UZ);
            verify(protectedService).getResume(501L,AppLanguage.UZ);
            verify(protectedService).getMyResumes(1,20,AppLanguage.UZ);
            verifyNoMoreInteractions(protectedService);
            clearInvocations(protectedService);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("buyer","",List.of(new SimpleGrantedAuthority("ROLE_BUYER"))));
            controller.getResume(501L,AppLanguage.UZ);
            verify(protectedService).getResume(501L,AppLanguage.UZ);
        }
    }
}