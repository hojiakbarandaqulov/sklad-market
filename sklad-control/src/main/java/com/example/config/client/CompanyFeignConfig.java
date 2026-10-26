package com.example.config.client;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

// CompanyClient va ProductClient uchun; boshqa Feign clientlarga global qo'llanmaydi.
public class CompanyFeignConfig {
    @Bean
    public RequestInterceptor companyBearerInterceptor() {
        return request -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwt && authentication.isAuthenticated()) {
                request.header("Authorization", "Bearer " + jwt.getToken().getTokenValue());
            }
        };
    }
}
