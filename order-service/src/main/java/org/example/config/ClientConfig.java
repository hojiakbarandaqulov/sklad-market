package org.example.config;

import feign.RequestInterceptor;
import org.example.service.ServiceTokenService;
import org.springframework.context.annotation.*;

@Configuration
public class ClientConfig {
    @Bean RequestInterceptor serviceAuthorization(ServiceTokenService tokens) {
        return request -> request.header("Authorization", "Bearer " + tokens.token());
    }
}
