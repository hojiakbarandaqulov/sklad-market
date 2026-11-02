package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "market_order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(nullable = false)
    private Long productId;

    // Buyurtma yaratilgan paytdagi nom va narx keyinchalik o'zgarmaydi.
    @Column(nullable = false, length = 1000)
    private String productName;

    @Column(precision = 19, scale = 3, nullable = false)
    private BigDecimal quantity;

    @Column(precision = 19, scale = 3, nullable = false)
    private BigDecimal unitPrice;

    @Column(precision = 38, scale = 6, nullable = false)
    private BigDecimal lineAmount;
}
