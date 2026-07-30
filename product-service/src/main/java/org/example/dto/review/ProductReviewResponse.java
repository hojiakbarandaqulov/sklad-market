package org.example.dto.review;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ProductReviewResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Long companyId;
    private Long buyerId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
