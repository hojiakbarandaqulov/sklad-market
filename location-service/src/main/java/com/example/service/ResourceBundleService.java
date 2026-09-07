package com.example.service;

import com.example.enums.AppLanguage;

public interface ResourceBundleService {
    String getMessage(String code, AppLanguage language);
}
