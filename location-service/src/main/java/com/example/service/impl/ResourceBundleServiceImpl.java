package com.example.service.impl;

import com.example.enums.AppLanguage;
import com.example.service.ResourceBundleService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ResourceBundleServiceImpl implements ResourceBundleService {

    private final MessageSource messageSource;

    @Override
    public String getMessage(String code, AppLanguage language) {
        AppLanguage resolvedLanguage = language == null ? AppLanguage.UZ : language;
        Locale locale = Locale.forLanguageTag(resolvedLanguage.name().toLowerCase(Locale.ROOT));
        return messageSource.getMessage(code, null, locale);
    }
}
