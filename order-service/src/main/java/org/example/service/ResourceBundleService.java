package org.example.service;

import org.example.enums.AppLanguage;
import java.util.Locale;

public interface ResourceBundleService {
    String getMessage(String code);
    String getMessage(String code, Locale locale);
    String getMessage(String code, AppLanguage language);
    String getMessage(String code, String language);
}
