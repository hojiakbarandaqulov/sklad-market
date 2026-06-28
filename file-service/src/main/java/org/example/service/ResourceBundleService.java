package org.example.service;

import org.example.enums.AppLanguage;

public interface ResourceBundleService {

     String getMessage(String code, AppLanguage lang);

     String getMessage(String code, String lang);
}
