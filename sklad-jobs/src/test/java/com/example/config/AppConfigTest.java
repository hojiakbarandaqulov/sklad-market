package com.example.config;

import com.example.enums.AppLanguage;
import org.junit.jupiter.api.Test;
import org.springframework.format.support.DefaultFormattingConversionService;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppConfigTest {

    private final DefaultFormattingConversionService conversionService =
            new DefaultFormattingConversionService();

    @Test
    void convertsCommonAcceptLanguageFormats() {
        new AppConfig().addFormatters(conversionService);

        assertEquals(AppLanguage.UZ, conversionService.convert("", AppLanguage.class));
        assertEquals(AppLanguage.UZ, conversionService.convert("uz-UZ", AppLanguage.class));
        assertEquals(AppLanguage.RU, conversionService.convert("ru-RU,ru;q=0.9", AppLanguage.class));
        assertEquals(AppLanguage.EN, conversionService.convert("en-US", AppLanguage.class));
        assertEquals(AppLanguage.UZ, conversionService.convert("de-DE", AppLanguage.class));
    }
}
