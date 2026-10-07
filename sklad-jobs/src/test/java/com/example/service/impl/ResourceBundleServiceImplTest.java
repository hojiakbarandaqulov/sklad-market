package com.example.service.impl;

import com.example.enums.AppLanguage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceBundleServiceImplTest {

    private final ResourceBundleServiceImpl messageService =
            new ResourceBundleServiceImpl(messageSource());

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void resolvesMessagesForUzbekEnglishAndRussian() {
        assertEquals(
                "Vakansiya topilmadi",
                messageService.getMessage("vacancy.not.found", AppLanguage.UZ)
        );
        assertEquals(
                "Vacancy not found",
                messageService.getMessage("vacancy.not.found", AppLanguage.EN)
        );
        assertEquals(
                "Вакансия не найдена",
                messageService.getMessage("vacancy.not.found", AppLanguage.RU)
        );
    }

    @Test
    void usesRequestLocaleAndFallsBackToUzbekLanguage() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ru"));
        assertEquals(
                "Ошибка проверки данных",
                messageService.getMessage("validation.failed")
        );

        assertEquals(
                "Vakansiya moderatsiyaga yuborildi",
                messageService.getMessage("vacancy.submit.success", null)
        );
    }

    private ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages/message");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.forLanguageTag("uz"));
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
