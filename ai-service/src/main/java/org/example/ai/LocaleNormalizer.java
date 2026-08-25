package org.example.ai;

import java.util.Locale;

/** Normalizes the platform's {@code Accept-Language: UZ|RU|EN} header into our lowercase locale codes. */
public final class LocaleNormalizer {

    public static final String DEFAULT_LOCALE = "ru";

    private LocaleNormalizer() {
    }

    public static String normalize(String rawLocale) {
        if (rawLocale == null || rawLocale.isBlank()) {
            return DEFAULT_LOCALE;
        }
        String normalized = rawLocale.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "uz", "ru", "en" -> normalized;
            default -> DEFAULT_LOCALE;
        };
    }
}
