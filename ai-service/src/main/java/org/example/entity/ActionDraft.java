package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A pending write action drafted by a tool (PLAN.md §4.2 item 3: "draft -&gt; confirm for every
 * mutation"). {@code status} moves DRAFT -&gt; CONFIRMED|CANCELLED|EXPIRED exactly once; only
 * {@link org.example.service.ActionDraftConfirmService} may flip it to CONFIRMED, and only by
 * calling the real platform endpoint with the confirming request's own JWT.
 */
@Entity
@Getter
@Setter
@Table(name = "action_draft")
public class ActionDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "user_sub", nullable = false)
    private String userSub;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
