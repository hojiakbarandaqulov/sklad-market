package org.example.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;
import org.example.enums.LeadSource;
import org.example.enums.LeadStatus;

import java.time.LocalDate;

@Entity
@Getter
@Setter
public class Lead extends BaseEntity {
    private Long buyerId;
    private Long acceptedOrderId;
    private Long sellerId;
    private Long companyId;
    @Enumerated(EnumType.STRING)
    private LeadSource source;
    @Enumerated(EnumType.STRING)
    private LeadStatus status = LeadStatus.NEW;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private String deliveryAddress;
    private LocalDate neededDate;
    private String comment;
    private String closeReason;
}
