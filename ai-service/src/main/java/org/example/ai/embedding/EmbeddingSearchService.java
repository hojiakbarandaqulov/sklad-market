package org.example.ai.embedding;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.ai.provider.EmbeddingProvider;
import org.example.dto.SearchResultItem;
import org.example.exception.AiNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Read side of the vector index: embeds a query and returns cosine top-K, and returns vector
 * neighbours of a product. Shared by the REST endpoints ({@code /api/v1/ai/search},
 * {@code /api/v1/ai/similar/{productId}}) and the two agent tools, so ranking/limits are identical
 * however they're reached.
 */
@Service
public class EmbeddingSearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final EmbeddingProvider embeddingProvider;
    private final ProductEmbeddingRepository repository;
    private final double sameCategoryBoost;
    private final Cache<SearchCacheKey, List<SearchResultItem>> searchCache;
    private final Cache<SimilarCacheKey, List<SearchResultItem>> similarCache;

    public EmbeddingSearchService(
            EmbeddingProvider embeddingProvider,
            ProductEmbeddingRepository repository,
            @Value("${ai.similar.category-boost:0.05}") double sameCategoryBoost) {
        this.embeddingProvider = embeddingProvider;
        this.repository = repository;
        this.sameCategoryBoost = sameCategoryBoost;
        this.searchCache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .build();
        this.similarCache = Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .build();
    }

    /** Semantic (cross-lingual) search: embed the query, return cosine top-K. */
    public List<SearchResultItem> search(String query, Integer limit) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Query 'q' must not be empty");
        }
        if (trimmed.length() > 300) {
            throw new IllegalArgumentException("Query 'q' must not exceed 300 characters");
        }
        int boundedLimit = boundedLimit(limit);
        return searchCache.get(new SearchCacheKey(trimmed, boundedLimit), key -> {
            float[] queryVector = embeddingProvider.embedQuery(key.query());
            return List.copyOf(repository.search(queryVector, key.limit()).stream().map(this::toItem).toList());
        });
    }

    /**
     * Vector neighbours of {@code productId} (excluding itself), with a same-category boost.
     * @throws AiNotFoundException if the product is not in the index.
     */
    public List<SearchResultItem> similar(long productId, Integer limit) {
        int boundedLimit = boundedLimit(limit);
        return similarCache.get(new SimilarCacheKey(productId, boundedLimit), key -> {
            Optional<List<EmbeddingSearchHit>> hits = repository.findSimilar(key.productId(), key.limit(), sameCategoryBoost);
            return List.copyOf(hits.orElseThrow(() -> new AiNotFoundException("Product not found in the index"))
                    .stream().map(this::toItem).toList());
        });
    }

    /**
     * Slug-keyed variant for the agent tool (the model gets slugs, not numeric ids, from search
     * results). Resolves the slug against the index, then delegates to {@link #similar(long, Integer)}.
     * @throws AiNotFoundException if the slug is not in the index.
     */
    public List<SearchResultItem> similarBySlug(String slug, Integer limit) {
        String trimmed = slug == null ? "" : slug.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Argument 'slug' must not be empty");
        }
        long productId = repository.findProductIdBySlug(trimmed)
                .orElseThrow(() -> new AiNotFoundException("Product not found in the index: " + trimmed));
        return similar(productId, limit);
    }

    private int boundedLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private SearchResultItem toItem(EmbeddingSearchHit hit) {
        double rounded = Math.round(hit.score() * 10000.0) / 10000.0;
        return new SearchResultItem(
                hit.productId(), hit.slug(), hit.name(), hit.categoryId(), hit.regionId(), hit.price(),
                hit.currency(), hit.imageUrl(), rounded);
    }

    private record SearchCacheKey(String query, int limit) {
    }

    private record SimilarCacheKey(long productId, int limit) {
    }
}
