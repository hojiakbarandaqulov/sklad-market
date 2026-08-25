package org.example.ai.provider;

import java.util.List;

/**
 * Vendor-neutral embedding access (PLAN.md §2 "Vendor isolation"): the indexer and search layer
 * depend only on this interface, so switching providers is config + one class. Vectors returned are
 * already L2-normalized (unit length) so cosine similarity reduces to a dot product and pgvector's
 * {@code <=>} cosine distance is well-behaved.
 */
public interface EmbeddingProvider {

    /**
     * Batch-embed catalog documents ({@code task_type=RETRIEVAL_DOCUMENT}). Order is preserved:
     * the i-th result corresponds to the i-th input text. Implementations chunk large inputs into
     * provider-safe batches internally.
     */
    List<float[]> embedDocuments(List<String> texts);

    /** Embed a single user query ({@code task_type=RETRIEVAL_QUERY}) for nearest-neighbour search. */
    float[] embedQuery(String text);

    /** The embedding dimensionality (e.g. 768) — must match the {@code vector(N)} column. */
    int dimension();
}
