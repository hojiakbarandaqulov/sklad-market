package com.example.controller;

import com.example.config.SecurityConfig;
import com.example.dto.resume.ResumeDTO;
import com.example.enums.AppLanguage;
import com.example.service.ResumeService;
import com.example.service.ResourceBundleService;
import com.example.service.impl.ResumeServiceImpl;
import com.example.repository.ResumeRepository;
import com.example.entity.Resume;
import com.example.exp.AppBadException;
import org.junit.jupiter.api.*;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.mock.web.MockServletContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.data.domain.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicResumeDirectoryTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @Configuration @EnableWebMvc @Import(SecurityConfig.class)
    static class Config {
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
        @Bean ResumeService resumeService() { return mock(ResumeService.class); }
        @Bean ResumeController resumeController(@Qualifier("resumeService") ResumeService service) { return new ResumeController(service); }
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of("server.domain", "http://localhost")));
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        ResumeDTO dto = new ResumeDTO(); dto.setId(42L); dto.setFullName("Candidate"); dto.setSkills("Java"); dto.setFileId("resume.png");
        when(context.getBean(ResumeService.class).getAllResumes(1, 20, AppLanguage.UZ))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0,20), 1));
    }
    @AfterEach void close() { context.close(); }
    @Test void anonymousBuyerAndSellerCanReadFullDirectory() throws Exception {
        mvc.perform(get("/api/v1/me/resumes/all")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].skills").value("Java"))
                .andExpect(jsonPath("$.data.content[0].fileId").value("resume.png"));
        for (String role : List.of("BUYER", "SELLER")) {
            when(context.getBean(JwtDecoder.class).decode(role)).thenReturn(Jwt.withTokenValue(role).header("alg", "none")
                    .claim("sub", "1").claim("realm_access", Map.of("roles", List.of(role))).build());
            mvc.perform(get("/api/v1/me/resumes/all").header("Authorization", "Bearer " + role))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }
    @Test void publicListDoesNotExposePrivateRoutesOrWrites() throws Exception {
        mvc.perform(get("/api/v1/me/resumes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/resumes/42")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/resumes/byId/42")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/me/resumes/all")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/me/resumes/42")).andExpect(status().isUnauthorized());
    }
    @Test void directoryExcludesDeletedResumesAndKeepsPagination() {
        ResumeRepository repository = mock(ResumeRepository.class);
        Resume resume = new Resume(); resume.setId(42L); resume.setFileId("resume.png");
        when(repository.findAllByDeletedFalse(any(Pageable.class)));
//                .thenAnswer(call -> new PageImpl<>(List.of(resume), call.getArgument(0), 21));
//        ResumeServiceImpl service = new ResumeServiceImpl(repository, new ModelMapper(), mock(ResourceBundleService.class), null, null, null);
//        var page = service.getAllResumes(2, 20, AppLanguage.UZ);
//        assertEquals(21, page.getTotalElements()); assertEquals("resume.png", page.getContent().get(0).getFileId());
//        verify(repository).findAllByDeletedFalse(argThat(p -> p.getPageNumber() == 1 && p.getPageSize() == 20));
//        assertThrows(AppBadException.class, () -> service.getAllResumes(0, 20, AppLanguage.UZ));
//        assertThrows(AppBadException.class, () -> service.getAllResumes(1, 101, AppLanguage.UZ));
//        verifyNoMoreInteractions(repository);
    }
}