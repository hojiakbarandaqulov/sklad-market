package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;
import org.example.enums.SupportParticipantRole;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "support_message", indexes = {
        @Index(name = "idx_support_message_thread_id", columnList = "thread_id,id")
})
public class SupportMessage extends BaseEntity {
    private Long threadId;
    private Long senderId;

    @Enumerated(EnumType.STRING)
    private SupportParticipantRole senderRole;

    @Column(columnDefinition = "TEXT")
    private String body;

    private String attachmentKey;
    private String attachmentUrl;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime requesterReadAt;
    private LocalDateTime adminReadAt;
}
