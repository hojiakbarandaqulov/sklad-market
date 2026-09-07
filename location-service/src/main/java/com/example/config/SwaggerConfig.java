package com.example.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
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

@SecurityScheme(
        name = "bearerAuth",
        description = "JWT bearer token",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
@Configuration
public class SwaggerConfig {

    @Value("${server.url}")
    private String url;

    @Bean
    public OpenAPI locationOpenAPI() {
        Server server = new Server();
        server.setUrl(url);
        server.setDescription("Server URL");

        return new OpenAPI()
                .info(new Info().title("Location Service API").version("1.0"))
                .servers(List.of(server))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
