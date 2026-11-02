package org.example.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI orderOpenAPI(@Value("${order.swagger.server-url}") String serverUrl) {
        Info apiInfo = new Info()
                .title("Order — Buyurtmalar API")
                .version("1.0")
                .description("Sklad Market buyurtmalari. Authorize tugmasiga xaridor yoki sotuvchining JWT access tokenini kiriting. "
                        + "Yaratishda mahsulot rezervlanadi, jo'natishda ombordan chiqariladi, bekor qilishda rezerv bo'shatiladi. "
                        + "Xaridor ID'si token ichidagi profileId dan olinadi. Sotuvchi ro'yxatida companyId majburiy.");

        SecurityScheme bearerAuth = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        Components components = new Components().addSecuritySchemes("bearerAuth", bearerAuth);

        // '/' brauzer ochgan hostni ishlatadi: Gateway'da ham, lokal portda ham API yo'li to'g'ri qoladi.
        Server server = new Server().url(serverUrl).description("Joriy API hosti");

        return new OpenAPI()
                .info(apiInfo)
                .servers(List.of(server))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
