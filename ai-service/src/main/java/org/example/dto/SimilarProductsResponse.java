package org.example.dto;

import java.util.List;

public record SimilarProductsResponse(long productId, int count, List<SearchResultItem> items) {
}
