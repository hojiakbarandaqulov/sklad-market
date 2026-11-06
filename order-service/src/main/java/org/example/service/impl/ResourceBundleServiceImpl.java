package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.enums.AppLanguage;
import org.example.service.ResourceBundleService;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Service;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ResourceBundleServiceImpl implements ResourceBundleService {
    private final ResourceBundleMessageSource resourceBundle;

    public String getMessage(String code) {
        return getMessage(code, LocaleContextHolder.getLocale());
    }

    public String getMessage(String code, AppLanguage language) {
        return getMessage(code, Locale.forLanguageTag(language.name()));
    }

    public String getMessage(String code, String language) {
        return getMessage(code, Locale.forLanguageTag(language));
    }

    public String getMessage(String code, Locale locale) {
        // Older orders contain the original stored failure reason.
        if ("Mahsulot qoldig'i yetarli emas yoki rezerv yaratib bo'lmadi".equals(code)) {
            code = "order.stock.unavailable";
        }
        return resourceBundle.getMessage(code, null, code, locale);
    }
}
