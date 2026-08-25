package org.example.ai.embedding;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real pgvector queries against a {@code pgvector/pgvector:pg16} Testcontainer — the {@code <=>}
 * cosine ranking, {@code CAST(? AS vector)} binding, and the upsert/hash/delete/similar operations
 * cannot be slice-tested. Skips cleanly (assumption) when Docker is unavailable (PLAN.md Phase 5:
 * "fall back ... if Docker is unavailable — say so"). Uses {@code vector(3)} for readable fixtures;
 * the repository code is dimension-agnostic (it binds text literals).
 */
class ProductEmbeddingRepositoryTest {

    private static PostgreSQLContainer<?> postgres;
    private static JdbcTemplate jdbc;
    private static ProductEmbeddingRepository repository;

    @BeforeAll
    static void startContainer() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available — skipping pgvector Testcontainers tests");
        postgres = new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));
        postgres.start();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("CREATE TABLE product_embedding ("
                + "product_id bigint PRIMARY KEY, slug varchar(255) NOT NULL, name varchar(512) NOT NULL, "
                + "category_id bigint, region_id bigint, price numeric, currency varchar(16), image_url text, "
                + "content_hash varchar(64) NOT NULL, embedding vector(3) NOT NULL, indexed_at timestamptz DEFAULT now())");
        jdbc.execute("CREATE INDEX ON product_embedding USING hnsw (embedding vector_cosine_ops)");
        repository = new ProductEmbeddingRepository(jdbc);
    }

    @AfterAll
    static void stopContainer() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void clean() {
        if (jdbc != null) {
            jdbc.execute("TRUNCATE product_embedding");
        }
    }

    private ProductEmbeddingRow row(long id, long categoryId, String hash, float... vec) {
        return new ProductEmbeddingRow(id, "slug-" + id, "Product " + id, categoryId, 7L, 100.0 + id, "UZS", hash, vec);
    }

    @Test
    void search_ranksByCosineSimilarityDescending() {
        repository.upsert(row(1, 10, "h1", 1f, 0f, 0f));
        repository.upsert(row(2, 10, "h2", 0f, 1f, 0f));
        repository.upsert(row(3, 20, "h3", 0.9f, 0.1f, 0f));

        List<EmbeddingSearchHit> hits = repository.search(new float[] {1f, 0f, 0f}, 3);

        assertThat(hits).hasSize(3);
        assertThat(hits.get(0).productId()).isEqualTo(1L);
        assertThat(hits.get(0).score()).isCloseTo(1.0, within(1e-4));
        assertThat(hits.get(1).productId()).isEqualTo(3L); // closer to [1,0,0] than [0,1,0]
        assertThat(hits.get(2).productId()).isEqualTo(2L);
        assertThat(hits.get(0).slug()).isEqualTo("slug-1");
        assertThat(hits.get(0).price()).isEqualTo(101.0);
    }

    @Test
    void upsert_onConflictUpdatesInPlace() {
        repository.upsert(row(1, 10, "h1", 1f, 0f, 0f));
        repository.upsert(new ProductEmbeddingRow(1, "slug-1-new", "Renamed", 10L, 7L, 5.0, "USD", "h1b", new float[] {0f, 1f, 0f}));

        assertThat(repository.count()).isEqualTo(1);
        List<EmbeddingSearchHit> hits = repository.search(new float[] {0f, 1f, 0f}, 1);
        assertThat(hits.get(0).name()).isEqualTo("Renamed");
        assertThat(hits.get(0).slug()).isEqualTo("slug-1-new");
    }

    @Test
    void findAllContentHashes_returnsProductIdToHash() {
        repository.upsert(row(1, 10, "hash-1", 1f, 0f, 0f));
        repository.upsert(row(2, 10, "hash-2", 0f, 1f, 0f));

        Map<Long, String> hashes = repository.findAllContentHashes();

        assertThat(hashes).containsOnly(Map.entry(1L, "hash-1"), Map.entry(2L, "hash-2"));
    }

    @Test
    void deleteByProductIds_removesGivenAndIsNoOpOnEmpty() {
        repository.upsert(row(1, 10, "h1", 1f, 0f, 0f));
        repository.upsert(row(2, 10, "h2", 0f, 1f, 0f));

        assertThat(repository.deleteByProductIds(Set.of(2L))).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.deleteByProductIds(Set.of())).isZero();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void findSimilar_excludesSelfAndAppliesCategoryBoost() {
        repository.upsert(row(1, 10, "h1", 1f, 0f, 0f));       // target, category 10
        repository.upsert(row(2, 10, "h2", 0f, 1f, 0f));       // same category, far by vector
        repository.upsert(row(3, 20, "h3", 0.99f, 0.01f, 0f)); // other category, very close by vector

        // No boost: nearest by pure vector distance wins (id3), self excluded.
        List<EmbeddingSearchHit> noBoost = repository.findSimilar(1, 5, 0.0).orElseThrow();
        assertThat(noBoost).extracting(EmbeddingSearchHit::productId).containsExactly(3L, 2L);

        // Strong same-category boost promotes the same-category product (id2) above the closer id3.
        List<EmbeddingSearchHit> boosted = repository.findSimilar(1, 5, 2.0).orElseThrow();
        assertThat(boosted.get(0).productId()).isEqualTo(2L);
    }

    @Test
    void findSimilar_returnsEmptyOptionalForUnindexedProduct() {
        assertThat(repository.findSimilar(999, 5, 0.05)).isEmpty();
    }

    @Test
    void findProductIdBySlug_resolvesIndexedSlug() {
        repository.upsert(row(42, 10, "h", 1f, 0f, 0f));
        assertThat(repository.findProductIdBySlug("slug-42")).contains(42L);
        assertThat(repository.findProductIdBySlug("missing")).isEmpty();
    }

    @Test
    void findDistinctRegionIds_canBeScopedToCategoryAndIsBounded() {
        repository.upsert(new ProductEmbeddingRow(1, "one", "One", 10L, 7L, 1.0, "UZS", "h1", new float[] {1f, 0f, 0f}));
        repository.upsert(new ProductEmbeddingRow(2, "two", "Two", 10L, 9L, 2.0, "UZS", "h2", new float[] {0f, 1f, 0f}));
        repository.upsert(new ProductEmbeddingRow(3, "three", "Three", 20L, 11L, 3.0, "UZS", "h3", new float[] {0f, 0f, 1f}));

        assertThat(repository.findDistinctRegionIds(null, 2)).containsExactly(7L, 9L);
        assertThat(repository.findDistinctRegionIds(10L, 20)).containsExactly(7L, 9L);
        assertThat(repository.findDistinctRegionIds(999L, 20)).isEmpty();
    }

    @Test
    void searchFiltered_appliesAllFiltersBeforeLimit() {
        repository.upsert(new ProductEmbeddingRow(1, "match", "Match", 10L, 7L, 100.0, "UZS", "h1", new float[] {1f, 0f, 0f}));
        repository.upsert(new ProductEmbeddingRow(2, "wrong-category", "Wrong", 20L, 7L, 100.0, "UZS", "h2", new float[] {1f, 0f, 0f}));
        repository.upsert(new ProductEmbeddingRow(3, "wrong-region", "Wrong", 10L, 8L, 100.0, "UZS", "h3", new float[] {1f, 0f, 0f}));
        repository.upsert(new ProductEmbeddingRow(4, "too-expensive", "Wrong", 10L, 7L, 500.0, "UZS", "h4", new float[] {1f, 0f, 0f}));
        repository.upsert(new ProductEmbeddingRow(5, "wrong-currency", "Wrong", 10L, 7L, 100.0, "USD", "h5", new float[] {1f, 0f, 0f}));

        List<EmbeddingSearchHit> hits = repository.searchFiltered(
                new float[] {1f, 0f, 0f}, 10L, 7L, 50.0, 200.0, "uzs", 5);

        assertThat(hits).extracting(EmbeddingSearchHit::productId).containsExactly(1L);
    }
}
