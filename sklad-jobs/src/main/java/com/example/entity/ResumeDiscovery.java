package com.example.entity;

import com.example.enums.ExperienceLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

/** Explicit, revocable professional-profile consent; never changes the private resume. */
@Entity @Table(name = "jobs_resume_discovery") @Getter @Setter
public class ResumeDiscovery {
    @Id private Long resumeId;
    @MapsId @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resume_id",foreignKey=@ForeignKey(name="fk_jobs_discovery_resume"))
    private Resume resume;
    @Column(nullable = false) private boolean enabled;
    @Column(length = 120) private String specialty;
    @Enumerated(EnumType.STRING) private ExperienceLevel experienceLevel;
    private Long regionId;
    @Column(length = 1600) private String skills;
    private Instant consentedAt;
    @Column(length = 20) private String consentVersion;
}
