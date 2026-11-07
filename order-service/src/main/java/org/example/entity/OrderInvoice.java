package org.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter
@Table(name="order_invoices")
public class OrderInvoice {
    @Id private Long orderId;
    @Column(nullable=false,length=128)
    private String idempotencyKey;
    @Column(nullable=false,length=64)
    private String requestHash;
    @Column(nullable=false,length=20)
    private String status;
    @Column(nullable=false,length=1048576)
    private String payload;
}
