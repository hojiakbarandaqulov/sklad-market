package org.example.ai.jobs;

import java.math.BigDecimal;
import java.util.*;

public record JobPreferences(String desiredPosition, List<String> skills, Long regionId,
        String experienceLevel, Long categoryId, BigDecimal salaryMin, String employmentType, String workSchedule) {
    public static final List<String> EXPERIENCE_LEVELS = List.of("NO_EXPERIENCE", "ONE_TO_THREE_YEARS", "THREE_TO_SIX_YEARS", "OVER_SIX_YEARS");

    public JobPreferences validated() {
        String position = text(desiredPosition, 120, "desiredPosition");
        if (position == null) throw new IllegalArgumentException("desiredPosition is required");
        if (skills == null || skills.isEmpty() || skills.size() > 20) throw new IllegalArgumentException("Provide 1 to 20 skills");
        Map<String, String> unique = new LinkedHashMap<>();
        for (String skill : skills) {
            String safe = text(skill, 60, "skills");
            if (safe == null) throw new IllegalArgumentException("Skills must not be blank");
            unique.putIfAbsent(safe.toLowerCase(Locale.ROOT), safe);
        }
        positive(regionId, "regionId", true);
        positive(categoryId, "categoryId", false);
        if (!EXPERIENCE_LEVELS.contains(experienceLevel)) throw new IllegalArgumentException("Select a valid experienceLevel");
        money(salaryMin);
        return new JobPreferences(position, List.copyOf(unique.values()), regionId, experienceLevel,
                categoryId, salaryMin, text(employmentType, 80, "employmentType"), text(workSchedule, 80, "workSchedule"));
    }

    static String text(String text, int limit, String field) {
        if (text == null || text.isBlank()) return null;
        String safe = text.trim();
        if (safe.length() > limit || safe.codePoints().anyMatch(c -> Character.isISOControl(c)))
            throw new IllegalArgumentException(field + " is too long or contains control characters");
        return safe;
    }
    static void positive(Long value, String field, boolean required) {
        if ((required && value == null) || (value != null && value <= 0)) throw new IllegalArgumentException(field + " must be positive");
    }
    static void money(BigDecimal value) {
        if (value != null && (value.signum() < 0 || value.precision() - value.scale() > 17 || Math.max(value.scale(), 0) > 2))
            throw new IllegalArgumentException("salaryMin must be a nonnegative UZS amount (up to two decimals)");
    }
}
