package org.example.entity;

import jakarta.persistence.*;
import org.example.enums.OrderStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "market_orders", uniqueConstraints = @UniqueConstraint(name = "uk_order_buyer_key", columnNames = {"buyer_id", "idempotency_key"}))
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false)
    private Long buyerId;
    @Column(unique=true)
    private Long leadId;

    @Column(nullable = false)
    private Long companyId;

    // Bir xaridorning takroriy so'rovi ikkinchi buyurtma yaratmasligi uchun.
    @Column(nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, precision = 38, scale = 6)
    private BigDecimal totalAmount;

    @Column(length = 2000)
    private String deliveryAddress;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(length = 2000)
    private String note;

    // Ombordagi rezerv Control bazasida saqlanadi; bu yerda faqat uning ID'si turadi.
    private Long reservationId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant shippedAt;

    private Instant cancelledAt;

    @Column(length = 1000)
    private String failureReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productId ASC")
    private List<OrderItem> items = new ArrayList<>();
}
