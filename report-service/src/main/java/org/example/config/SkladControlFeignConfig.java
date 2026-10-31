package org.example.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Faqat SkladControl clientiga qo'llanadi; sotuvchining JWT tokenini ombor servisiga uzatadi. */
public class SkladControlFeignConfig {
    @Bean
    public RequestInterceptor skladControlBearerInterceptor() {
        return request -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwt && authentication.isAuthenticated()) {
                request.header("Authorization", "Bearer " + jwt.getToken().getTokenValue());
            }
        };
    }
}
