package org.example.config;

import feign.RequestInterceptor;
import org.example.service.ServiceTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClientConfig {

    @Bean
    public RequestInterceptor serviceAuthorization(ServiceTokenService serviceTokenService) {
        // Feign ichki so'rovga Order service-account tokenini qo'shadi.
        return requestTemplate -> {
            if (requestTemplate.headers().keySet().stream().anyMatch("Authorization"::equalsIgnoreCase)) return;
            String accessToken = serviceTokenService.token();
            requestTemplate.header("Authorization", "Bearer " + accessToken);
        };
    }
}
