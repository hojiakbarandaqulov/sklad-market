package org.example.ai.tool.impl;

import org.example.ai.embedding.EmbeddingSearchService;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.example.dto.SearchResultItem;
import org.example.exception.AiNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FindSimilarProductsToolTest {

    private final EmbeddingSearchService searchService = mock(EmbeddingSearchService.class);
    private final FindSimilarProductsTool tool = new FindSimilarProductsTool(searchService);

    private ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub", "token", Set.of(), "ru");
    }

    @Test
    void isRoleOpen() {
        assertThat(tool.allowedRoles()).isEmpty();
        assertThat(tool.name()).isEqualTo("find_similar_products");
    }

    @Test
    void execute_projectsNeighbours() {
        when(searchService.similarBySlug(eq("cement-m500"), any())).thenReturn(List.of(
                new SearchResultItem(8L, "cement-m400", "Цемент М400", 3L, 1L, 42000.0, "UZS", 0.9)));

        ToolResult result = tool.execute(Map.of("slug", "cement-m500"), context());

        assertThat(result.success()).isTrue();
        List<?> items = (List<?>) result.data().get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) items.get(0);
        assertThat(first).containsEntry("slug", "cement-m400");
    }

    @Test
    void execute_missingSlug_isToolError() {
        ToolResult result = tool.execute(Map.of(), context());
        assertThat(result.success()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(400);
    }

    @Test
    void execute_unindexedSlug_isNotFound() {
        when(searchService.similarBySlug(eq("ghost"), any()))
                .thenThrow(new AiNotFoundException("Product not found in the index: ghost"));

        ToolResult result = tool.execute(Map.of("slug", "ghost"), context());

        assertThat(result.success()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(400); // ToolResult.notFound uses 400 (platform's not-found convention)
    }
}
