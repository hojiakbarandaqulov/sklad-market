package org.example.dto.review;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CompanyRatingDto {
    private Double getAverageRating;
    private Long getReviewCount;
}
