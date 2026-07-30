package org.example.dto.review;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ProductReviewListResponse {
    private List<ProductReviewResponse> items;
    private int page;
    private int perPage;
    private long totalElements;
    private int totalPages;
    private BigDecimal averageRating;
    private long reviewCount;
}
