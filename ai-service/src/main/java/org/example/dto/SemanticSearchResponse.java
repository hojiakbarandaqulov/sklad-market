package org.example.dto;

import java.util.List;

public record SemanticSearchResponse(String query, int count, List<SearchResultItem> items) {
}
