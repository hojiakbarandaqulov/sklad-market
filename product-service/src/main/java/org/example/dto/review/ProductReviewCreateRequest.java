package org.example.dto.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductReviewCreateRequest {

    @NotNull(message = "rating required")
    @Min(value = 1, message = "{review.rating.range}")
    @Max(value = 5, message = "{review.rating.range}")
    private Integer rating;

    @Size(max = 1000, message = "comment required")
    private String comment;
}
