package org.example.ai.business.service;

import org.example.ai.provider.EmbeddingProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessQueryEmbeddingServiceTest {

    @Test
    void normalizesAndCachesPaidQueryEmbedding() {
        EmbeddingProvider provider = mock(EmbeddingProvider.class);
        float[] vector = {1f, 0f};
        when(provider.embedQuery("cement")).thenReturn(vector);
        BusinessQueryEmbeddingService service = new BusinessQueryEmbeddingService(provider);

        assertThat(service.embed(" Cement ")).isSameAs(vector);
        assertThat(service.embed("cement")).isSameAs(vector);
        verify(provider, times(1)).embedQuery("cement");
    }
}
