package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;
import org.example.enums.CompanyMemberRole;

@Entity
@Getter
@Setter
@Table(
        name = "company_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_company_members_company_profile",
                columnNames = {"company_id", "profile_id"}),
        indexes = @Index(name = "idx_company_members_profile_deleted", columnList = "profile_id, deleted")
)
public class CompanyMember extends BaseEntity {

    @Column(nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private Long profileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CompanyMemberRole role;
}
