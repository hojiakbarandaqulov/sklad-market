package org.example.config;

import feign.RequestInterceptor;
import org.example.service.ServiceTokenService;
import org.springframework.context.annotation.Bean;

public class ClientConfig {

    @Bean
    public RequestInterceptor serviceAuthorization(ServiceTokenService serviceTokenService) {
        // Faqat shu konfiguratsiyani tanlagan Feign clientga service-account tokeni qo'shiladi.
        return requestTemplate -> {
            if (requestTemplate.headers().keySet().stream().anyMatch("Authorization"::equalsIgnoreCase)) return;
            String accessToken = serviceTokenService.token();
            requestTemplate.header("Authorization", "Bearer " + accessToken);
        };
    }
}
