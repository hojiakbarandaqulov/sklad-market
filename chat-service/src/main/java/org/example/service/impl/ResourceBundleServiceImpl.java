package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.service.ResourceBundleService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ResourceBundleServiceImpl implements ResourceBundleService {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("uz", "ru", "en");

    private final MessageSource messageSource;

    @Override
    public String getMessage(String code, Object... arguments) {
        Locale requestLocale = LocaleContextHolder.getLocale();
        String language = requestLocale == null ? "uz" : requestLocale.getLanguage().toLowerCase(Locale.ROOT);
        Locale locale = Locale.forLanguageTag(SUPPORTED_LANGUAGES.contains(language) ? language : "uz");
        return messageSource.getMessage(code, arguments, locale);
    }
}
