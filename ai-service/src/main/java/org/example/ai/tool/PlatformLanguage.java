package org.example.ai.tool;

import java.util.Locale;
import java.util.Set;

/** Converts our lowercase conversation locale ("uz"/"ru"/"en") to the platform's {@code Accept-Language: UZ|RU|EN} convention. */
public final class PlatformLanguage {

    private static final Set<String> SUPPORTED = Set.of("UZ", "RU", "EN");

    private PlatformLanguage() {
    }

    public static String header(String locale) {
        if (locale == null || locale.isBlank()) {
            return "RU";
        }
        String upper = locale.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED.contains(upper) ? upper : "RU";
    }
}
