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
import org.example.enums.AssignedAdminRole;
import org.example.enums.RequesterRole;
import org.example.enums.SupportThreadStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "support_thread", indexes = {
        @Index(name = "idx_support_thread_requester", columnList = "requester_id,requester_role,status"),
        @Index(name = "idx_support_thread_admin", columnList = "assigned_admin_id,status"),
        @Index(name = "idx_support_thread_last_message", columnList = "last_message_at")
})
public class SupportThread extends BaseEntity {
    private Long requesterId;

    @Enumerated(EnumType.STRING)
    private RequesterRole requesterRole;

    private Long assignedAdminId;

    @Enumerated(EnumType.STRING)
    private AssignedAdminRole assignedAdminRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportThreadStatus status = SupportThreadStatus.OPEN;

    @Column(length = 300)
    private String subject;

    private LocalDateTime lastMessageAt;
}
