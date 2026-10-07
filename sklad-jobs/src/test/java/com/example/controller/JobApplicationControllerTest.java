package com.example.controller;
import com.example.dto.application.*;
import com.example.enums.AppLanguage;
import com.example.exp.AppConflictException;
import com.example.exp.handle.ExceptionHandleController;
import com.example.service.*;
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
class JobApplicationControllerTest {
    JobApplicationService service=mock(JobApplicationService.class);
    MockMvc mvc;
    @BeforeEach void setup(){
        ResourceBundleService messages=mock(ResourceBundleService.class);
        when(messages.getMessage(anyString())).thenAnswer(i->i.getArgument(0));
        mvc=MockMvcBuilders.standaloneSetup(new JobApplicationController(service),new VacancyApplicationController(service)).setControllerAdvice(new ExceptionHandleController(messages)).build();
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    @Test void replacementRequiresPositiveResumeId() throws Exception {
        for(String body:List.of("{}","{\"resumeId\":0}"))
            mvc.perform(patch("/api/v1/me/applications/9001/resume").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void replacementAndWithdrawRoutesWork() throws Exception {
        JobApplicationDTO dto=new JobApplicationDTO();dto.setId(9001L);dto.setResumeId(502L);
        when(service.replaceResume(eq(9001L),any(),eq(AppLanguage.UZ))).thenReturn(dto);
        mvc.perform(patch("/api/v1/me/applications/9001/resume").contentType(MediaType.APPLICATION_JSON).content("{\"resumeId\":502}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.resumeId").value(502));
        when(service.withdraw(9001L,AppLanguage.UZ)).thenThrow(new AppConflictException("Invalid state"));
        mvc.perform(post("/api/v1/me/applications/9001/withdraw")).andExpect(status().isConflict());
    }
    @Configuration @EnableMethodSecurity(proxyTargetClass=true)
    static class Config {
        @Bean JobApplicationService service(){return mock(JobApplicationService.class);}
        @Bean VacancyApplicationController createController(JobApplicationService s){return new VacancyApplicationController(s);}
        @Bean JobApplicationController controller(JobApplicationService s){return new JobApplicationController(s);}
    }
    @Test void allActionsRequireBuyerRole(){
        try(var ctx=new AnnotationConfigApplicationContext(Config.class)){
            var c=ctx.getBean(JobApplicationController.class);
            var s=ctx.getBean(JobApplicationService.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("seller","",List.of(new SimpleGrantedAuthority("ROLE_SELLER"))));
            assertThrows(AccessDeniedException.class,()->c.getMyApplications(null,1,20,AppLanguage.UZ));
            assertThrows(AccessDeniedException.class,()->c.getMyApplication(1L,AppLanguage.UZ));
            assertThrows(AccessDeniedException.class,()->c.withdraw(1L,AppLanguage.UZ));
            assertThrows(AccessDeniedException.class,()->c.replaceResume(1L,new ApplicationResumeRequest(),AppLanguage.UZ));
            assertThrows(AccessDeniedException.class,()->ctx.getBean(VacancyApplicationController.class).create(100L,new ApplicationCreateRequest(),AppLanguage.UZ));
            verifyNoInteractions(s);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("buyer","",List.of(new SimpleGrantedAuthority("ROLE_BUYER"))));
            c.getMyApplication(1L,AppLanguage.UZ);verify(s).getMyApplication(1L,AppLanguage.UZ);
        }
    }
    @Test void createValidatesBodyAndReturns201() throws Exception {
        String body="""
            {"resumeId":501,"fullName":"Ali","phone":"+998901234567",
             "email":"ali@example.com","regionId":1,"consentAccepted":true}
            """;
        for(String invalid:List.of("{}",body.replace("true","false"),body.replace("501","null"),body.replace("ali@example.com","invalid"))){
            mvc.perform(post("/api/v1/vacancies/100/applications").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        JobApplicationDTO dto=new JobApplicationDTO();dto.setId(9001L);dto.setResumeId(501L);
        when(service.create(eq(100L),any(),eq(AppLanguage.UZ))).thenReturn(dto);
        mvc.perform(post("/api/v1/vacancies/100/applications").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(9001));
    }    @Test void sellerActionsRequireSellerAndVacancyListHasDistinctRoute() throws Exception {
        when(service.getMyApplication(1L,AppLanguage.UZ)).thenReturn(new JobApplicationDTO());
        mvc.perform(get("/api/v1/me/applications/1")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/applications/vacancy/100")).andExpect(status().isOk());
        verify(service).getApplicationVacancy(100L,com.example.enums.GetNewApplicationStatus.NEW,1,20);
        try(var ctx=new AnnotationConfigApplicationContext(Config.class)) {
            var c=ctx.getBean(JobApplicationController.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("buyer","",List.of(new SimpleGrantedAuthority("ROLE_BUYER"))));
            assertThrows(AccessDeniedException.class,()->c.applicationResponse(1L,com.example.enums.ApplicationResponseStatus.ACCEPTED,AppLanguage.UZ));
            assertThrows(AccessDeniedException.class,()->c.getApplicationVacancy(100L,com.example.enums.GetNewApplicationStatus.NEW,1,20,AppLanguage.UZ));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("seller","",List.of(new SimpleGrantedAuthority("ROLE_SELLER"))));
            c.applicationResponse(1L,com.example.enums.ApplicationResponseStatus.ACCEPTED,AppLanguage.UZ);
            verify(ctx.getBean(JobApplicationService.class)).applicationStatusResponse(1L,com.example.enums.ApplicationResponseStatus.ACCEPTED,AppLanguage.UZ);
        }
    }}