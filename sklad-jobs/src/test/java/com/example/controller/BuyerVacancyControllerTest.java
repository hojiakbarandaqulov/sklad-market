package com.example.controller;

import com.example.dto.vacancy.VacancyRequest;
import com.example.enums.AppLanguage;
import com.example.service.BuyerVacancyService;
import com.example.service.VacancyService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BuyerVacancyControllerTest {
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean BuyerVacancyService buyerService() { return mock(BuyerVacancyService.class); }
        @Bean VacancyService vacancyService() { return mock(VacancyService.class); }
        @Bean BuyerVacancyController controller(BuyerVacancyService buyer, VacancyService vacancy) {
            return new BuyerVacancyController(buyer, vacancy);
        }
    }
    @Test void sellerCannotCallBuyerMutationsAndBuyerCan() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(BuyerVacancyController.class);
            var service = context.getBean(BuyerVacancyService.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "seller", "", List.of(new SimpleGrantedAuthority("ROLE_SELLER"))));
            assertThrows(AccessDeniedException.class, () -> controller.close(10L, AppLanguage.UZ));
            assertThrows(AccessDeniedException.class, () -> controller.archive(10L, AppLanguage.UZ));
            assertThrows(AccessDeniedException.class, () -> controller.update(10L, new VacancyRequest(), AppLanguage.UZ));
            verifyNoInteractions(service);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "buyer", "", List.of(new SimpleGrantedAuthority("ROLE_BUYER"))));
            controller.close(10L, AppLanguage.UZ);
            controller.archive(10L, AppLanguage.UZ);
            verify(service).close(10L, AppLanguage.UZ);
            verify(service).archive(10L, AppLanguage.UZ);
        } finally { SecurityContextHolder.clearContext(); }
    }
}