package org.example.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity @Getter @Setter @Table(name = "market_order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id") private Order order;
    @Column(nullable = false) private Long productId;
    @Column(nullable = false, length = 1000) private String productName;
    @Column(precision = 19, scale = 3, nullable = false) private BigDecimal quantity;
    @Column(precision = 19, scale = 3, nullable = false) private BigDecimal unitPrice;
    @Column(precision = 38, scale = 6, nullable = false) private BigDecimal lineAmount;
}
