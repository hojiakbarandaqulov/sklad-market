package com.example.controller;

import com.example.dto.category.JobCategoryDTO;
import com.example.dto.category.JobCategoryRequest;
import com.example.enums.AppLanguage;
import com.example.service.JobCategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JobCategoryControllerTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean JobCategoryService service() { return mock(JobCategoryService.class); }
        @Bean AdminJobCategoryController controller(JobCategoryService service) {
            return new AdminJobCategoryController(service);
        }
    }

    @Test void onlyAdminCanUseCategoryCrud() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(AdminJobCategoryController.class);
            for (String role : List.of("BUYER", "SELLER")) {
                authenticate(role);
                assertThrows(AccessDeniedException.class, () -> controller.create(new JobCategoryRequest(), AppLanguage.UZ));
                assertThrows(AccessDeniedException.class, () -> controller.update(1L, new JobCategoryRequest(), AppLanguage.UZ));
                assertThrows(AccessDeniedException.class, () -> controller.delete(1L, AppLanguage.UZ));
                assertThrows(AccessDeniedException.class, () -> controller.list(1, 20, AppLanguage.UZ));
                assertThrows(AccessDeniedException.class, () -> controller.get(1L, AppLanguage.UZ));
            }
            verifyNoInteractions(context.getBean(JobCategoryService.class));
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test void adminRoutesValidateTranslationsAndPublicDetailUsesRequestedLanguage() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            authenticate("ADMIN");
            var service = context.getBean(JobCategoryService.class);
            var category = new JobCategoryDTO(3L, "ИТ", "IT", "ИТ", "IT", true);
            when(service.create(any(), eq(AppLanguage.RU))).thenReturn(category);
            var mvc = MockMvcBuilders.standaloneSetup(context.getBean(AdminJobCategoryController.class),
                    new JobCategoryController(service)).build();
            mvc.perform(post("/api/v1/admin/job-categories").contentType("application/json")
                    .header("Accept-Language", "RU")
                    .content("{\"nameUz\":\"IT\",\"nameRu\":\"ИТ\",\"nameEn\":\"IT\",\"isActive\":true}"))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(3));
            mvc.perform(post("/api/v1/admin/job-categories").contentType("application/json")
                    .content("{\"nameUz\":\" \",\"nameRu\":\"IT\",\"nameEn\":\"IT\"}"))
                    .andExpect(status().isBadRequest());
            when(service.get(3L, true, AppLanguage.RU)).thenReturn(category);
            mvc.perform(get("/api/v1/job-categories/3").header("Accept-Language", "RU"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("ИТ"));
            mvc.perform(delete("/api/v1/admin/job-categories/3")).andExpect(status().isNoContent());
            verify(service).delete(3L, AppLanguage.UZ);
        } finally { SecurityContextHolder.clearContext(); }
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                role, "", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
