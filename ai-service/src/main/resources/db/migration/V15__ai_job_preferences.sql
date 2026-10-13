-- AI owns a compact opt-in matching profile, never a copy of a Jobs resume/contact record.
CREATE TABLE ai_job_preferences (
    user_sub VARCHAR(255) PRIMARY KEY,
    desired_position VARCHAR(120) NOT NULL,
    skills_json TEXT NOT NULL,
    region_id BIGINT NOT NULL CHECK (region_id > 0),
    experience_level VARCHAR(30) NOT NULL CHECK (experience_level IN
        ('NO_EXPERIENCE','ONE_TO_THREE_YEARS','THREE_TO_SIX_YEARS','OVER_SIX_YEARS')),
    category_id BIGINT CHECK (category_id > 0),
    salary_min NUMERIC(19,2) CHECK (salary_min >= 0),
    employment_type VARCHAR(80),
    work_schedule VARCHAR(80),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
