package org.example.dto.event;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ProductCreatedEvent {
    private Long productId;
    private Long companyId;
    private Long categoryId;
    private Long sellerId;
    private String name;
    private String slug;
    private Double price;
    private String currency;
    private String moderationStatus;
    private LocalDateTime createdAt;
}
