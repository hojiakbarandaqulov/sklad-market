package com.example.controller;

import com.example.dto.favorite.VacancyFavoriteResponse;
import com.example.enums.AppLanguage;
import com.example.service.VacancyFavoriteService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VacancyFavoriteControllerTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean VacancyFavoriteService service() { return mock(VacancyFavoriteService.class); }
        @Bean VacancyFavoriteController controller(VacancyFavoriteService service) {
            return new VacancyFavoriteController(service);
        }
    }
    @Test void bothRolesCanUseRoutesWithProductCompatibleResponse() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var service = context.getBean(VacancyFavoriteService.class);
            when(service.add(10L, AppLanguage.UZ)).thenReturn(new VacancyFavoriteResponse(true));
            when(service.remove(10L, AppLanguage.UZ)).thenReturn(new VacancyFavoriteResponse(false));
            var mvc = MockMvcBuilders.standaloneSetup(context.getBean(VacancyFavoriteController.class)).build();
            for (var role : List.of("BUYER", "SELLER")) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        role, "", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
                mvc.perform(post("/api/v1/vacancy-favorites/10"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.favorited").value(true));
                mvc.perform(delete("/api/v1/vacancy-favorites/10"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.data.favorited").value(false));
                mvc.perform(get("/api/v1/vacancy-favorites?page=2&perPage=10")).andExpect(status().isOk());
                mvc.perform(get("/api/v1/vacancy-favorites/count")).andExpect(status().isOk());
            }
            verify(service, times(2)).getFavorites(2, 10, AppLanguage.UZ);
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void otherRolesCannotCallFavorites() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "admin", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            var controller = context.getBean(VacancyFavoriteController.class);
            assertThrows(AccessDeniedException.class, () -> controller.add(10L, AppLanguage.UZ));
            assertThrows(AccessDeniedException.class, () -> controller.remove(10L, AppLanguage.UZ));
            assertThrows(AccessDeniedException.class, () -> controller.getFavorites(1, 20, AppLanguage.UZ));
            assertThrows(AccessDeniedException.class, () -> controller.getCount(AppLanguage.UZ));
            verifyNoInteractions(context.getBean(VacancyFavoriteService.class));
        } finally { SecurityContextHolder.clearContext(); }
    }
}