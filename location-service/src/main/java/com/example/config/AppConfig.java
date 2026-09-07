package com.example.config;

import com.example.enums.AppLanguage;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Locale;

@Configuration
public class AppConfig implements WebMvcConfigurer {

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages/message");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setDefaultLocale(Locale.forLanguageTag("uz"));
        return messageSource;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, AppLanguage.class, source -> {
            if (source == null || source.isBlank()) {
                return AppLanguage.UZ;
            }

            String normalized = source.trim().split(",")[0].trim().split(";")[0].trim();
            normalized = normalized.split("[-_]")[0].toUpperCase(Locale.ROOT);

            return switch (normalized) {
                case "RU" -> AppLanguage.RU;
                case "EN" -> AppLanguage.EN;
                default -> AppLanguage.UZ;
            };
        });
    }
}
