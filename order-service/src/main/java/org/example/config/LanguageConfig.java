package org.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.MessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.Validator;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import java.util.List;
import java.util.Locale;

@Configuration
public class LanguageConfig implements WebMvcConfigurer {
    @Bean
    public static org.springframework.context.support.ResourceBundleMessageSource messageSource() {
        var source = new org.springframework.context.support.ResourceBundleMessageSource();
        source.setBasenames("messages/message");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.forLanguageTag("uz"));
        source.setFallbackToSystemLocale(false);
        return source;
    }
    private final MessageSource messageSource;

    public LanguageConfig(MessageSource messageSource) { this.messageSource = messageSource; }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.forLanguageTag("uz"),
                Locale.forLanguageTag("ru"), Locale.ENGLISH));
        resolver.setDefaultLocale(Locale.forLanguageTag("uz"));
        return resolver;
    }

    @Bean
    public LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }

    @Override
    public Validator getValidator() { return validator(); }

    @Override
    public void addFormatters(org.springframework.format.FormatterRegistry registry) {
        registry.addConverter(String.class, org.example.enums.AppLanguage.class,
                value -> {
                    String tag = value.trim().split(",")[0].split(";")[0].split("[-_]")[0];
                    return switch (tag.toLowerCase(Locale.ROOT)) {
                        case "ru" -> org.example.enums.AppLanguage.RU;
                        case "en" -> org.example.enums.AppLanguage.EN;
                        default -> org.example.enums.AppLanguage.UZ;
                    };
                });
    }
}
