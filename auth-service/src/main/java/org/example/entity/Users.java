package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;
import org.example.enums.GeneralStatus;
import org.example.enums.Roles;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
public class Users extends BaseEntity {

    private String firstName;

    private String lastName;

    @Column(unique = true)
    private String username;

    private String password;

    @Column(unique = true)
    private String keycloakId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Roles role;

    @Enumerated(EnumType.STRING)
    private GeneralStatus status;

    private Integer failedLoginCount = 0;        // 5 ta xato → lock

    private LocalDateTime lockedUntil;       // 15 daqiqa lock

    private LocalDateTime lastLoginAt;

}
