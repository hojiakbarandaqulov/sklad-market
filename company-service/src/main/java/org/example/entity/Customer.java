package org.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;

@Getter
@Setter
@Entity
@Table(name = "customer", indexes = @Index(name = "idx_customer_company_deleted", columnList = "company_id,deleted"))
public class Customer extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 100)
    private String contractNumber;

    @Column(length = 30)
    private String phone;

    @Column(nullable = false)
    private Integer paymentTermDays;

    private String contractAttachId;
    private String contractFileName;
    private String contractContentType;
    private Long contractSizeBytes;

    @Version
    private Long version;
}
