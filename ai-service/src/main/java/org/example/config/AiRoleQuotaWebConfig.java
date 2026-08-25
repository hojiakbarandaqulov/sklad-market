package org.example.config;

import org.example.ai.guardrail.AiRoleQuotaInterceptor;
import org.example.ai.guardrail.AiRoleQuotaService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class AiRoleQuotaWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<AiRoleQuotaService> quotaService;

    public AiRoleQuotaWebConfig(ObjectProvider<AiRoleQuotaService> quotaService) {
        this.quotaService = quotaService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        quotaService.ifAvailable(service -> registry
                .addInterceptor(new AiRoleQuotaInterceptor(service))
                .addPathPatterns("/api/v1/ai/**"));
    }
}
