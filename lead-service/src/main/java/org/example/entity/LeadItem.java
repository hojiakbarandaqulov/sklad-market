package org.example.entity;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.example.entity.base.BaseEntity;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
public class LeadItem extends BaseEntity {
    private Long leadId;
    private Long productId;
    private String productNameSnapshot;
    private BigDecimal priceSnapshot;
    @jakarta.persistence.Column(precision=19,scale=3)
    private BigDecimal quantity;
}
