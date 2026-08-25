package org.example.ai.tool.impl;

import org.example.ai.embedding.EmbeddingSearchService;
import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolResult;
import org.example.dto.SearchResultItem;
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

class SemanticSearchProductsToolTest {

    private final EmbeddingSearchService searchService = mock(EmbeddingSearchService.class);
    private final SemanticSearchProductsTool tool = new SemanticSearchProductsTool(searchService);

    private ToolExecutionContext context() {
        return new ToolExecutionContext(UUID.randomUUID(), "sub", "token", Set.of(), "ru");
    }

    @Test
    void isRoleOpen() {
        assertThat(tool.allowedRoles()).isEmpty();
        assertThat(tool.name()).isEqualTo("semantic_search_products");
    }

    @Test
    void execute_projectsResults() {
        when(searchService.search(eq("оптовый рис"), any())).thenReturn(List.of(
                new SearchResultItem(7L, "rice", "Guruch", 3L, 1L, 9000.0, "UZS", 0.83)));

        ToolResult result = tool.execute(Map.of("query", "оптовый рис"), context());

        assertThat(result.success()).isTrue();
        List<?> items = (List<?>) result.data().get("items");
        assertThat(items).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) items.get(0);
        assertThat(first).containsEntry("slug", "rice").containsEntry("score", 0.83);
        assertThat(first).doesNotContainKey("productId"); // compact projection, no internal id leaked
    }

    @Test
    void execute_missingQuery_isToolError() {
        ToolResult result = tool.execute(Map.of(), context());
        assertThat(result.success()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(400);
    }

    @Test
    void execute_providerError_isReportedAsToolError_notThrown() {
        when(searchService.search(any(), any()))
                .thenThrow(new AiChatException(AiErrorCode.PROVIDER_ERROR, "down"));

        ToolResult result = tool.execute(Map.of("query", "рис"), context());

        assertThat(result.success()).isFalse();
    }
}
