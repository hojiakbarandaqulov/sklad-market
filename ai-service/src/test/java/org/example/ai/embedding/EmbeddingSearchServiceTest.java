package org.example.ai.embedding;

import org.example.ai.provider.EmbeddingProvider;
import org.example.dto.SearchResultItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingSearchServiceTest {

    @Test
    void repeatedIdenticalSearchUsesShortLivedCache() {
        EmbeddingProvider provider = mock(EmbeddingProvider.class);
        ProductEmbeddingRepository repository = mock(ProductEmbeddingRepository.class);
        float[] vector = new float[] {1f, 0f, 0f};
        when(provider.embedQuery("cement")).thenReturn(vector);
        when(repository.search(vector, 10)).thenReturn(List.of(
                new EmbeddingSearchHit(1L, "cement", "Cement", 2L, 3L, 10.0, "UZS", 0.9)));
        EmbeddingSearchService service = new EmbeddingSearchService(provider, repository, 0.05);

        List<SearchResultItem> first = service.search(" cement ", null);
        List<SearchResultItem> second = service.search("cement", null);

        assertThat(first).isEqualTo(second).hasSize(1);
        verify(provider, times(1)).embedQuery("cement");
        verify(repository, times(1)).search(vector, 10);
    }

    @Test
    void repeatedSimilarLookupUsesShortLivedCache() {
        EmbeddingProvider provider = mock(EmbeddingProvider.class);
        ProductEmbeddingRepository repository = mock(ProductEmbeddingRepository.class);
        when(repository.findSimilar(7L, 5, 0.05)).thenReturn(java.util.Optional.of(List.of(
                new EmbeddingSearchHit(8L, "brick", "Brick", 2L, 3L, 10.0, "UZS", 0.8))));
        EmbeddingSearchService service = new EmbeddingSearchService(provider, repository, 0.05);

        assertThat(service.similar(7L, 5)).isEqualTo(service.similar(7L, 5));

        verify(repository, times(1)).findSimilar(7L, 5, 0.05);
    }

    @Test
    void rejectsOversizedEmbeddingQueriesBeforeCallingProvider() {
        EmbeddingProvider provider = mock(EmbeddingProvider.class);
        EmbeddingSearchService service = new EmbeddingSearchService(
                provider, mock(ProductEmbeddingRepository.class), 0.05);

        assertThatThrownBy(() -> service.search("x".repeat(301), 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("300");
        verify(provider, times(0)).embedQuery(org.mockito.ArgumentMatchers.anyString());
    }
}
