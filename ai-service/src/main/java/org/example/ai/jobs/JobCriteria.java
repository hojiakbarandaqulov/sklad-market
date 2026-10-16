package org.example.ai.jobs;

import java.math.BigDecimal;
import java.util.List;

/** Explicit chat filters are hard constraints; saved constraints are defaults, never silently relaxed. */
public record JobCriteria(String query, Long regionId, Long categoryId, BigDecimal salaryMin,
        String experienceLevel, String employmentType, String workSchedule, int limit) {
    public JobCriteria validated(boolean queryRequired) {
        String safeQuery = JobPreferences.text(query, 120, "query");
        if (queryRequired && safeQuery == null && categoryId == null) throw new IllegalArgumentException("Specify a job title or actual job category");
        JobPreferences.positive(regionId, "regionId", false);
        JobPreferences.positive(categoryId, "categoryId", false);
        JobPreferences.money(salaryMin);
        if (experienceLevel != null && !JobPreferences.EXPERIENCE_LEVELS.contains(experienceLevel)) throw new IllegalArgumentException("Invalid experienceLevel");
        if (limit < 1 || limit > 5) throw new IllegalArgumentException("limit must be between 1 and 5");
        return new JobCriteria(safeQuery, regionId, categoryId, salaryMin, experienceLevel,
                JobPreferences.text(employmentType, 80, "employmentType"), JobPreferences.text(workSchedule, 80, "workSchedule"), limit);
    }
    public static JobCriteria defaults(int limit) { return new JobCriteria(null, null, null, null, null, null, null, limit); }
}
