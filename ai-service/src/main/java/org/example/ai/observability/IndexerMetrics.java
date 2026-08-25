package org.example.ai.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.ai.embedding.ProductEmbeddingRepository;
import org.example.ai.embedding.ProductIndexer;
import org.springframework.stereotype.Component;

/**
 * Registers indexer gauges (PLAN.md Phase 7 "indexer stats") WITHOUT touching {@link ProductIndexer}
 * itself — it reads the already-exposed in-memory {@link ProductIndexer#isRunning()} flag and the
 * embedding row count. Historical per-run outcomes/notes live in the {@code index_state} table (see
 * README "Observability" for the query) and via {@code GET /api/v1/ai/admin/reindex/status}.
 */
@Component
public class IndexerMetrics {

    public IndexerMetrics(MeterRegistry registry, ProductIndexer indexer, ProductEmbeddingRepository embeddingRepository) {
        Gauge.builder("ai.indexer.running", indexer, i -> i.isRunning() ? 1.0 : 0.0)
                .description("1 while a catalog reindex pass is in progress, else 0")
                .register(registry);
        Gauge.builder("ai.indexer.embeddings", embeddingRepository, repo -> (double) repo.count())
                .description("Number of product embedding rows currently indexed")
                .register(registry);
    }
}
