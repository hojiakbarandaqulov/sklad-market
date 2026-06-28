package org.example.service.impl;

import org.example.enums.AppLanguage;
import org.example.service.ResourceBundleService;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ResourceBundleServiceImpl implements ResourceBundleService {
    private final ResourceBundleMessageSource resourceBundle;

    public ResourceBundleServiceImpl(ResourceBundleMessageSource resourceBundle) {
        this.resourceBundle = resourceBundle;
    }

    @Override
    public String getMessage(String code, AppLanguage lang) {
        return resourceBundle.getMessage(code,null, new Locale(lang.name()));
    }

    @Override
    public String getMessage(String code, String lang) {
        return resourceBundle.getMessage(code,null,new Locale(lang));
    }
}
