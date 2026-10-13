package org.example.ai.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Repository
public class JobPreferencesStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public JobPreferencesStore(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public Optional<JobPreferences> find(String owner) {
        requireOwner(owner);
        return jdbc.query("SELECT * FROM ai_job_preferences WHERE user_sub=?", (rs, row) -> {
            try { return new JobPreferences(rs.getString("desired_position"), mapper.readValue(rs.getString("skills_json"), new TypeReference<List<String>>() {}),
                    rs.getLong("region_id"), rs.getString("experience_level"), rs.getObject("category_id", Long.class),
                    rs.getBigDecimal("salary_min"), rs.getString("employment_type"), rs.getString("work_schedule")); }
            catch (Exception invalid) { throw new IllegalStateException("Unable to read job preferences", invalid); }
        }, owner).stream().findFirst();
    }

    @Transactional
    public JobPreferences save(String owner, JobPreferences preferences) {
        requireOwner(owner);
        JobPreferences safe = preferences.validated();
        try {
            jdbc.update("""
                    INSERT INTO ai_job_preferences(user_sub,desired_position,skills_json,region_id,experience_level,category_id,salary_min,employment_type,work_schedule)
                    VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT (user_sub) DO UPDATE SET
                    desired_position=EXCLUDED.desired_position,skills_json=EXCLUDED.skills_json,region_id=EXCLUDED.region_id,
                    experience_level=EXCLUDED.experience_level,category_id=EXCLUDED.category_id,salary_min=EXCLUDED.salary_min,
                    employment_type=EXCLUDED.employment_type,work_schedule=EXCLUDED.work_schedule,updated_at=now()
                    """, owner, safe.desiredPosition(), mapper.writeValueAsString(safe.skills()), safe.regionId(), safe.experienceLevel(),
                    safe.categoryId(), safe.salaryMin(), safe.employmentType(), safe.workSchedule());
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalArgumentException("Invalid skills", invalid); }
        return safe;
    }

    public void delete(String owner) { requireOwner(owner); jdbc.update("DELETE FROM ai_job_preferences WHERE user_sub=?", owner); }
    private static void requireOwner(String owner) { if (owner == null || owner.isBlank()) throw new org.springframework.security.access.AccessDeniedException("Sign in to use saved job preferences"); }
}
