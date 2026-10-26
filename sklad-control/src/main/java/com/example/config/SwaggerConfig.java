package com.example.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SwaggerConfig {
    @Bean
    public OpenAPI controlOpenAPI(@Value("${server.url}") String url) {
        return new OpenAPI()
                .info(new Info().title("Sklad Control API").version("1.0"))
                .servers(List.of(new Server().url(url)))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
