package org.example.ai.business.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.ai.provider.EmbeddingProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/** Bounded cache so repeated discovery requests do not repeatedly incur paid query embeddings. */
@Service
public class BusinessQueryEmbeddingService {

    private final EmbeddingProvider provider;
    private final Cache<String, float[]> cache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    public BusinessQueryEmbeddingService(EmbeddingProvider provider) {
        this.provider = provider;
    }

    public float[] embed(String query) {
        String key = query.trim().toLowerCase(Locale.ROOT);
        return cache.get(key, provider::embedQuery);
    }
}
