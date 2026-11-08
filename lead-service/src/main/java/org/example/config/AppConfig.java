package org.example.config;

import org.example.enums.AppLanguage;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
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
    public org.springframework.web.servlet.LocaleResolver localeResolver() {
        org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver resolver = new org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(java.util.List.of(Locale.forLanguageTag("uz"),Locale.forLanguageTag("ru"),Locale.ENGLISH));
        resolver.setDefaultLocale(Locale.forLanguageTag("uz")); return resolver;
    }
    @Override
    public org.springframework.validation.Validator getValidator() {
        org.springframework.validation.beanvalidation.LocalValidatorFactoryBean validator = new org.springframework.validation.beanvalidation.LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource()); return validator;
    }

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages/message");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setDefaultLocale(new Locale("UZ"));
        return messageSource;
    }

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        return modelMapper;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, AppLanguage.class, source -> {
            if (source == null || source.isBlank()) {
                return AppLanguage.UZ;
            }

            String normalized = source.trim().split(",")[0].trim().split(";")[0].trim();
            normalized = normalized.split("[-_]")[0].toUpperCase(Locale.ROOT);

            if ("RU".equals(normalized)) {
                return AppLanguage.RU;
            }
            if ("EN".equals(normalized)) {
                return AppLanguage.EN;
            }
            return AppLanguage.UZ;
        });
    }
}
