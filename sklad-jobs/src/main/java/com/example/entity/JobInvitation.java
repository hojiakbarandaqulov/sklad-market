package com.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name = "jobs_ai_invitation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_jobs_invitation_request", columnNames = {"inviter_id", "request_id"})
}) @Getter @Setter
public class JobInvitation {
    public enum Status { PENDING, DECLINED, WITHDRAWN, ACCEPTED, EXPIRED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long inviterId;
    @Column(nullable = false) private Long candidateId;
    @Column(nullable = false) private Long resumeId;
    @Column(nullable = false) private Long vacancyId;
    @Column(nullable = false, length = 36) private String requestId;
    @Column(length = 1000) private String message;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;
}
