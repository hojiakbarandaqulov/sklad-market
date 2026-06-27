package org.example.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.entity.base.BaseEntity;
import org.example.enums.GeneralStatus;
import org.example.enums.Roles;
@Getter
@Setter
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfile extends BaseEntity {

    @Column(unique = true, nullable = false)
    private Long userId;

    private String firstName;
    private String lastName;
    private String username;
    private String position;
    private String telegram;
    @Column(length = 13)
    private String extraPhone;
    private String password;

    @Column(unique = true)
    private String keycloakId;

    @Enumerated(EnumType.STRING)
    private GeneralStatus status;

    @Enumerated(EnumType.STRING)
    private Roles roles;

    private Integer warningCount=0;

    @ManyToOne
    private Attach photo;
}
