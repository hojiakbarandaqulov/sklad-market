package org.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;
import org.example.enums.NotificationCreateType;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {
    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationCreateType type;

    @Column(nullable = false,columnDefinition = "TEXT")
    private String payloadJson;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    private LocalDateTime readAt;
}
