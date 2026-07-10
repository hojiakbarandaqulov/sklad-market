package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;
import org.example.enums.VerificationStatus;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class Company extends BaseEntity {
    @Column(nullable = false)
    private Long ownerUserId;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String slug;

    private String shortDescription;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String logoPath;

    private String coverUrl;

    @Column(unique = true)
    private String stir;

    private String phonePrimary;

    private String phoneSecondary;

    private String website;

    private String address;

    private String lat;
    private String lng;

    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus = VerificationStatus.DRAFT;

    private LocalDateTime verifiedAt;

    private String rejectReason;

    private Boolean isBlocked = false;

    private LocalDateTime deletedAt;
}
