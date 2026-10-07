package com.example.controller;
import com.example.config.SecurityConfig;
import com.example.dto.vacancy.*;
import com.example.enums.AppLanguage;
import com.example.exp.handle.ExceptionHandleController;
import com.example.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.mock.web.MockServletContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.domain.PageImpl;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class PublicVacancyControllerTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    VacancyService service;
    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class Config {
        @Bean JwtDecoder jwtDecoder(){return mock(JwtDecoder.class);}
        @Bean VacancyService vacancyService(){return mock(VacancyService.class);}
        @Bean VacancyController vacancyController(VacancyService s){return new VacancyController(s);}
        @Bean PublicVacancyController controller(VacancyService s){return new PublicVacancyController(s);}
        @Bean ResourceBundleService messages(){ResourceBundleService s=mock(ResourceBundleService.class); when(s.getMessage(anyString())).thenAnswer(i->i.getArgument(0));return s;}
        @Bean ExceptionHandleController errors(ResourceBundleService s){return new ExceptionHandleController(s);}
    }
    @BeforeEach void setup(){
        context=new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",Map.of("server.domain","http://localhost")));
        context.register(Config.class);context.refresh();
        service=context.getBean(VacancyService.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }
    @AfterEach void close(){context.close();}
    @Test void anonymousCanReadListAndDetails() throws Exception {
        when(service.getVacancies(any(),eq(AppLanguage.UZ))).thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0,20),0));
        PublicVacancyDTO dto=new PublicVacancyDTO();dto.setId(100L);
        when(service.getVacancy(100L,AppLanguage.UZ)).thenReturn(dto);
        mvc.perform(get("/api/v1/vacancies")).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        mvc.perform(get("/api/v1/vacancies/100")).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(100));
    }
    @Test void anonymousCannotWriteOrReachPrivatePaths() throws Exception {
        mvc.perform(post("/api/v1/vacancies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/resumes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/vacancies/100/applications")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void invalidFiltersFailBeforeQueryingService() throws Exception {
        mvc.perform(get("/api/v1/vacancies").param("page","0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/vacancies").param("perPage","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/vacancies").param("salaryMin","200").param("salaryMax","100")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void moderationQueueRequiresAdminAndReturnsFullDetails() throws Exception {
        mvc.perform(get("/api/v1/vacancy/admin/moderation-queue")).andExpect(status().isUnauthorized());
        var decoder = context.getBean(JwtDecoder.class);
        for (String role : List.of("SELLER", "ADMIN")) {
            var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue(role)
                    .header("alg", "RS256").subject("test")
                    .claim("realm_access", Map.of("roles", List.of(role))).build();
            when(decoder.decode(role)).thenReturn(jwt);
        }
        mvc.perform(get("/api/v1/vacancy/admin/moderation-queue").header("Authorization", "Bearer SELLER"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
        VacancyDTO dto = new VacancyDTO(); dto.setId(42L); dto.setPositionName("Operator");
        dto.setRequirements("Excel"); dto.setVacancyStatus(com.example.enums.VacancyStatus.UNDER_MODERATION);
        when(service.getModerationQueue(1, 12, AppLanguage.UZ)).thenReturn(new PageImpl<>(List.of(dto), org.springframework.data.domain.PageRequest.of(0,12), 1));
        mvc.perform(get("/api/v1/vacancy/admin/moderation-queue").header("Authorization", "Bearer ADMIN"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print()).andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(42))
                .andExpect(jsonPath("$.data.content[0].requirements").value("Excel"));
    }}